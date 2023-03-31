package fr.abes.kafkaconvergence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.abes.kafkaconvergence.dto.*;
import fr.abes.kafkaconvergence.entity.basexml.notice.NoticeXml;
import fr.abes.kafkaconvergence.exception.IllegalPpnException;
import fr.abes.kafkaconvergence.logger.Logger;
import fr.abes.kafkaconvergence.utils.TYPE_SUPPORT;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BestPpnService {
    private final WsService service;

    private final Logger logger;

    private LoggerResultDto loggerResultDto;

    private Map<String, Long> ppnElecList;

    private List<String> ppnPrintListFromOnlineId2Ppn;

    private List<String> ppnPrintListFromPrintId2Ppn;

    @Value("${score.online.id.to.ppn}")
    private long scoreOnlineId2Ppn;

    @Value("${score.print.id.to.ppn}")
    private long scorePrintId2Ppn;

    @Value("${score.error.type.notice}")
    private long scoreErrorType;

    @Value("${score.dat.to.ppn}")
    private long scoreDat2Ppn;

    private NoticeService noticeService;

    public List<String> sortByBestPpn(Map<String, Long> list){
        List<String> result = new ArrayList<>();
        result.add(Collections.max(list.entrySet(), Map.Entry.comparingByValue()).getKey());
        return result;
    }

    public List<String> getBestPpn(LigneKbartDto kbart, String provider) throws IOException, IllegalPpnException {
        List<String> result = new ArrayList<>();
        this.ppnElecList = new HashMap<>();
        this.ppnPrintListFromOnlineId2Ppn = new ArrayList<>();
        this.ppnPrintListFromPrintId2Ppn = new ArrayList<>();
        if (!kbart.getOnline_identifier().isEmpty() && !kbart.getPublication_type().isEmpty()) {
            feedPpnListFromOnline(kbart.getOnline_identifier(), kbart.getPublication_type(), provider);
            feedPpnListFromPrint(kbart.getPrint_identifier(), kbart.getPublication_type(), provider);
            if (this.ppnElecList.isEmpty()) {
                feedPpnListFromDat(kbart.getDate_monograph_published_online(), kbart.getPublication_title(), kbart.getAuthor(), kbart.getDate_monograph_published_print());
            }
        }
        if(!this.ppnElecList.isEmpty()){
            result = sortByBestPpn(this.ppnElecList);
        }
        return result;
    }

    public void feedPpnListFromOnline(String onlineIdentifier, String publicationType, String provider) throws JsonProcessingException {
        ResultWsSudocDto resultCallOnlineId2Ppn = service.callOnlineId2Ppn(publicationType, onlineIdentifier, provider);
        if (!resultCallOnlineId2Ppn.getPpns().isEmpty()) {
            long nbPpnElec = resultCallOnlineId2Ppn.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getType().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
            for (PpnWithTypeDto ppn : resultCallOnlineId2Ppn.getPpns()) {
                if (ppn.getType().equals(TYPE_SUPPORT.ELECTRONIQUE)) {
                    this.ppnElecList.put(ppn.getPpn(), scoreOnlineId2Ppn / nbPpnElec);
                } else if (ppn.getType().equals(TYPE_SUPPORT.IMPRIME)) {
                    this.ppnPrintListFromOnlineId2Ppn.add(ppn.getPpn());
                }
            }
            if(nbPpnElec > 1){
                //TODO logger.info("onlineId2Ppn à renvoyé" + nbPpnElec + "notices electroniques"); -> trouver comment avoir une même clé pr une ligne kbart
            }
        }
    }

    public void feedPpnListFromPrint(String printIdentifier, String publicationType, String provider) throws JsonProcessingException {
        ResultWsSudocDto resultPrintId2Ppn = service.callPrintId2Ppn(publicationType, printIdentifier, provider);
        if(!resultPrintId2Ppn.getPpns().isEmpty()) {
            long nbPpnElec = resultPrintId2Ppn.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getType().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
                for (PpnWithTypeDto ppn : resultPrintId2Ppn.getPpns()) {
                    if (ppn.getType().equals(TYPE_SUPPORT.ELECTRONIQUE)) {
                        this.ppnElecList.put(ppn.getPpn(), scorePrintId2Ppn /nbPpnElec);
                    } else if (ppn.getType().equals(TYPE_SUPPORT.IMPRIME)) {
                        this.ppnPrintListFromPrintId2Ppn.add(ppn.getPpn());
                    }
                }
            if(nbPpnElec > 1){
                //TODO gestion des logs -> log.info("PrintId2Ppn à renvoyé" + nbPpnElec + "notices electroniques");
            }
        }
    }

    public void feedPpnListFromDat(String monographPublishedOnline, String publicationTitle, String author, String dateMonographPublishedPrint) throws IOException, IllegalPpnException {
        this.loggerResultDto.setServiceName("dat2Ppn");
        if (!monographPublishedOnline.isEmpty()){
            ResultDat2PpnWebDto result = service.callDat2Ppn(monographPublishedOnline, author, publicationTitle);
            for (String ppn : result.getPpns()) {
                NoticeXml notice = noticeService.getNoticeByPpn(ppn);
                if (notice.isNoticeElectronique()) {
                    ppnElecList.put(ppn, scoreDat2Ppn);
                }
            }
        }
        if (!ppnElecList.isEmpty() && !dateMonographPublishedPrint.isEmpty()) {
            ResultDat2PpnWebDto result = service.callDat2Ppn(dateMonographPublishedPrint, author, publicationTitle);
            for (String ppn : result.getPpns()) {
                NoticeXml notice = noticeService.getNoticeByPpn(ppn);
                if (notice.isNoticeImprimee()) {
                    //TODO gérer cas notice imprimée
                }
            }
        } else {
            // TODO si toujours aucune notice n’est retournée, ajouter un message informant de l’absence de PPN sur ce kbart.
        }
    }

    private void sendLogIfError() throws JsonProcessingException {
        if(!this.loggerResultDto.getMessages().isEmpty()){
            logger.error(String.valueOf(this.loggerResultDto.getLigneKbartDto().hashCode()), this.loggerResultDto);
        }
    }
}

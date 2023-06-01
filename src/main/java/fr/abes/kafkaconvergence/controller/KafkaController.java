package fr.abes.kafkaconvergence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.kafkaconvergence.dto.LigneKbartDto;
import fr.abes.kafkaconvergence.exception.BestPpnException;
import fr.abes.kafkaconvergence.exception.IllegalFileFormatException;
import fr.abes.kafkaconvergence.exception.IllegalPpnException;
import fr.abes.kafkaconvergence.service.BestPpnService;
import fr.abes.kafkaconvergence.service.EmailServiceImpl;
import fr.abes.kafkaconvergence.service.TopicProducer;
import fr.abes.kafkaconvergence.utils.CheckFiles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Tag(name = "Convergence localhost", description = "Convergence localhost managements APIs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/v1")
public class KafkaController {
    private final TopicProducer topicProducer;
    private final BestPpnService service;
    private static final String HEADER_TO_CHECK = "publication_title";

    @Autowired
    private EmailServiceImpl emailServiceImpl;

    @Operation(
            summary = "Sends the best PPN to Kafka",
            description = "Reads a TSV file, calculates the best PPN and sends the answer to Kafka",
            responses = {
                    @ApiResponse( responseCode = "200", description = "The file has been correctly processed.", content = { @Content(schema = @Schema()) } ),
                    @ApiResponse( responseCode = "400", description = "An element of the query is badly formulated.", content = { @Content(schema = @Schema()) } ),
                    @ApiResponse( responseCode = "500", description = "An internal server error interrupted processing.", content = { @Content(schema = @Schema()) } ),
            }
    )
    @PostMapping(value = "/kbart2Kafka", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, name = "kbart2kafka")
    public void kbart2kafka(@Parameter(description = "A .tsv (Tabulation-Separated Values) file.", required = true) @RequestParam("file") MultipartFile file) throws IOException, BestPpnException, IllegalPpnException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            CheckFiles.verifyFile(file, HEADER_TO_CHECK);
            String provider = CheckFiles.getProviderFromFilename(file);
            //  Créer une liste pour stocker les statistiques de l'analyse du kbart
            List<LigneKbartDto> dataLines = new ArrayList<>();
            //lecture fichier, ligne par ligne, creation objet java pour chaque ligne
            String line;
            int nbLines = 0;
            int nbLinesWithBestPpn = 0;
            int nbLinesWithoutBestPpn = 0;
            //ajout nom du fichier dans contexte applicatif pour récupération par log4j
            ThreadContext.put("package", file.getOriginalFilename());
            while ((line = reader.readLine()) != null) {
                if (!line.contains(HEADER_TO_CHECK)) {
                    String[] tsvElementsOnOneLine = line.split("\t");
                    // Crée un nouvel objet dto et set les différentes parties
                    LigneKbartDto ligneKbartDto = constructDto(tsvElementsOnOneLine);
                    // Vérifie si un best ppn a déjà été renseigné
                    if (ligneKbartDto.isBestPpnEmpty()) {
                        String bestPpn = service.getBestPpn(ligneKbartDto, provider);
                        ligneKbartDto.setBestPpn(bestPpn);
                        dataLines.add(ligneKbartDto);
                    }
                    if(ligneKbartDto.isBestPpnEmpty()){
                        nbLinesWithoutBestPpn++;
                    } else {
                        nbLinesWithBestPpn++;
                    }
                    nbLines++;
                    topicProducer.sendKbart(ligneKbartDto);
                }
            }
            //  Envoi du mail récapitulatif
            emailServiceImpl.sendMailWithAttachment(Objects.requireNonNull(file.getOriginalFilename()).substring(0 ,file.getOriginalFilename().length() - 4), dataLines);
            log.info("{ nbLines : " + nbLines + ", nbLinesWithBestPpn : " + nbLinesWithBestPpn + ", nbLinesWithoutBestPpn : " + nbLinesWithoutBestPpn + " }");
        } catch (IllegalFileFormatException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Une url dans un champ title_url du kbart n'est pas correcte");
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Construction de la dto
     *
     * @param line ligne en entrée
     * @return Un objet DTO initialisé avec les informations de la ligne
     */
    private LigneKbartDto constructDto(String[] line) {
        LigneKbartDto kbartLineInDtoObject = new LigneKbartDto();
        kbartLineInDtoObject.setPublication_title(line[0]);
        kbartLineInDtoObject.setPrint_identifier(line[1]);
        kbartLineInDtoObject.setOnline_identifier(line[2]);
        kbartLineInDtoObject.setDate_first_issue_online(line[3]);
        kbartLineInDtoObject.setNum_first_vol_online(Integer.getInteger(line[4]));
        kbartLineInDtoObject.setNum_first_issue_online(Integer.getInteger(line[5]));
        kbartLineInDtoObject.setDate_last_issue_online(line[6]);
        kbartLineInDtoObject.setNum_last_vol_online(Integer.getInteger(line[7]));
        kbartLineInDtoObject.setNum_last_issue_online(Integer.getInteger(line[8]));
        kbartLineInDtoObject.setTitle_url(line[9]);
        kbartLineInDtoObject.setFirst_author(line[10]);
        kbartLineInDtoObject.setTitle_id(line[11]);
        kbartLineInDtoObject.setEmbargo_info(line[12]);
        kbartLineInDtoObject.setCoverage_depth(line[13]);
        kbartLineInDtoObject.setNotes(line[14]);
        kbartLineInDtoObject.setPublisher_name(line[15]);
        kbartLineInDtoObject.setPublication_type(line[16]);
        kbartLineInDtoObject.setDate_monograph_published_print(line[17]);
        kbartLineInDtoObject.setDate_monograph_published_online(line[18]);
        kbartLineInDtoObject.setMonograph_volume(Integer.getInteger(line[19]));
        kbartLineInDtoObject.setMonograph_edition(line[20]);
        kbartLineInDtoObject.setFirst_editor(line[21]);
        kbartLineInDtoObject.setParent_publication_title_id(line[22]);
        kbartLineInDtoObject.setPreceding_publication_title_id(line[23]);
        kbartLineInDtoObject.setAccess_type(line[24]);
        // Vérification de la présence d'un best ppn déjà renseigné dans le kbart
        if (line.length == 26){
            kbartLineInDtoObject.setBestPpn(line[25]);
        }
        return kbartLineInDtoObject;
    }

    /**
     * Sérialisation d'un objet dto en chaine de caractère pour le passer au producteur de messages kafka
     *
     * @param dto objet à passer au producteur de messages
     * @return une chaine à passer au TopicProducer de kafka
     */
    private String serializeDTO(LigneKbartDto dto) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(dto);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}

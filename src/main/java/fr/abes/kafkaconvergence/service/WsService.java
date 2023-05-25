package fr.abes.kafkaconvergence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.kafkaconvergence.dto.ResultDat2PpnWebDto;
import fr.abes.kafkaconvergence.dto.ResultWsSudocDto;
import fr.abes.kafkaconvergence.dto.SearchDatWebDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class WsService {
    @Value("${url.onlineId2Ppn}")
    private String urlOnlineId2Ppn;

    @Value("${url.printId2Ppn}")
    private String urlPrintId2Ppn;

    @Value("${url.dat2Ppn}")
    private String urlDat2Ppn;

    @Value("${url.doi2Ppn}")
    private String urlDoi2Ppn;

    private final RestTemplate restTemplate;
    private final HttpHeaders headers;

    private final ObjectMapper mapper;

    public WsService(ObjectMapper mapper, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        this.mapper = mapper;
    }


    public String postCall(String url, String requestJson) {
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate.postForObject(url, entity, String.class);
    }

    public String getCall(String url, String... params) {
        StringBuilder formedUrl = new StringBuilder(url);
        for (String param : params) {
            formedUrl.append("/");
            formedUrl.append(param);
        }
        log.info(formedUrl.toString());
        return restTemplate.getForObject(formedUrl.toString(), String.class);
    }

    public ResultWsSudocDto callOnlineId2Ppn(String type, String id, @Nullable String provider) throws JsonProcessingException {
        return mapper.readValue((provider != "") ? getCall(urlOnlineId2Ppn, type, id, provider) : getCall(urlOnlineId2Ppn, type, id), ResultWsSudocDto.class);
    }

    public ResultWsSudocDto callPrintId2Ppn(String type, String id, @Nullable String provider) throws JsonProcessingException {
        return mapper.readValue((provider != "") ? getCall(urlPrintId2Ppn, type, id, provider) : getCall(urlPrintId2Ppn, type, id), ResultWsSudocDto.class);
    }

    public ResultDat2PpnWebDto callDat2Ppn(String date, String author, String title) throws JsonProcessingException {
        SearchDatWebDto searchDatWebDto = new SearchDatWebDto(title);
        if(!author.isEmpty()){
            searchDatWebDto.setAuteur(author);
        }
        if(!date.isEmpty()){
            searchDatWebDto.setDate(Integer.valueOf(date));
        }
        return mapper.readValue(postCall(urlDat2Ppn, mapper.writeValueAsString(searchDatWebDto)), ResultDat2PpnWebDto.class);
    }

    public ResultWsSudocDto callDoi2Ppn(String doi, @Nullable String provider) throws JsonProcessingException {
        return mapper.readValue(getCall(urlDoi2Ppn, doi, provider), ResultWsSudocDto.class);
    }

}

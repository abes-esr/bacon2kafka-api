package fr.abes.kafkaconvergence.service;

import fr.abes.kafkaconvergence.entity.basexml.notice.Datafield;
import fr.abes.kafkaconvergence.entity.basexml.notice.NoticeXml;
import fr.abes.kafkaconvergence.entity.basexml.notice.SubField;
import fr.abes.kafkaconvergence.exception.IllegalPpnException;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.net.URISyntaxException;

@SpringBootTest(classes = {CheckUrlService.class})
class CheckUrlServiceTest {
    @Autowired
    CheckUrlService checkUrlService;
    @MockBean
    NoticeService noticeService;

    @Test
    void checkUrlInNoticeFirstCase() throws IllegalPpnException, IOException, URISyntaxException {
        Assertions.assertTrue(checkUrlService.checkUrlInNotice("111111111", null));
        Assertions.assertTrue(checkUrlService.checkUrlInNotice("111111111", "doi.org"));
        Assertions.assertTrue(checkUrlService.checkUrlInNotice("111111111", "http://doi.org/test"));
    }

    @Test
    void checkUrlInNoticeNullNotice() throws IllegalPpnException, IOException, URISyntaxException {
        NoticeXml notice = new NoticeXml();
        Mockito.when(noticeService.getNoticeByPpn("111111111")).thenReturn(notice);
        Assertions.assertFalse(checkUrlService.checkUrlInNotice("111111111", "http://wwww.test.com/"));
    }

    @Test
    void checkUrlInNotice856() throws IllegalPpnException, IOException, URISyntaxException {
        NoticeXml notice = new NoticeXml();
        SubField dollaru = new SubField();
        dollaru.setCode("u");
        dollaru.setValue("www.test.com");
        Datafield zone856 = new Datafield();
        zone856.setTag("856");
        zone856.setSubFields(Lists.newArrayList(dollaru));
        notice.setDatafields(Lists.newArrayList(zone856));
        Mockito.when(noticeService.getNoticeByPpn("111111111")).thenReturn(notice);
        Assertions.assertTrue(checkUrlService.checkUrlInNotice("111111111", "http://www.test.com/"));
    }

    @Test
    void checkUrlInNotice858() throws IllegalPpnException, IOException, URISyntaxException {
        NoticeXml notice = new NoticeXml();
        SubField dollaru = new SubField();
        dollaru.setCode("u");
        dollaru.setValue("www.test.com");
        Datafield zone859 = new Datafield();
        zone859.setTag("859");
        zone859.setSubFields(Lists.newArrayList(dollaru));
        notice.setDatafields(Lists.newArrayList(zone859));
        Mockito.when(noticeService.getNoticeByPpn("111111111")).thenReturn(notice);
        Assertions.assertTrue(checkUrlService.checkUrlInNotice("111111111", "http://www.test.com/"));
    }
}
package liquidmountain.web;

import liquidmountain.domain.qr.Color;
import liquidmountain.domain.qr.Image;
import liquidmountain.domain.qr.QrGenerator;
import liquidmountain.domain.qr.QrQrCodeMonkey;
import liquidmountain.repository.QrRepository;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@RestController
public class QrController {
    private static final Logger LOGGER = LoggerFactory.getLogger(QrController.class);

    @Autowired
    QrRepository qrRepository;

    @PostMapping(value = "/api/urls/{urlHash:[a-zA-Z0-9]+}/qrs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> createNewQr(@PathVariable String urlHash,
                                              @RequestPart("bg") String bg,
                                              @RequestPart("fg") String fg,
                                              @RequestPart(value = "logoImg", required = false) MultipartFile logoImg) {
        try {
            Image logoImage = null;
            if (logoImg != null) {
                logoImage = new Image(logoImg.getInputStream(), MediaType.parseMediaType(logoImg.getContentType()));
            }
            QrGenerator qrGenerator = new QrQrCodeMonkey(urlHash, new Color(fg), new Color(bg), logoImage);
            int id = qrRepository.saveQr(qrGenerator);
            HttpHeaders headers = new HttpHeaders();
            // TODO: Find a better way to do this. I don't like that the URL is hardcoded.
            headers.setLocation(new URI("http://localhost:8080/api/urls/" + urlHash + "/qrs/" + id));
            return new ResponseEntity<>("", headers, HttpStatus.CREATED);
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("When creating a new QR", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/api/urls/{urlHash:[a-zA-Z0-9]+}/qrs/{qrId:[0-9]+}")
    public ResponseEntity<byte[]> getQr(@PathVariable String urlHash, @PathVariable int qrId) {
        try {
            QrGenerator qrGenerator = qrRepository.retrieveQr(urlHash, qrId);
            Image qrImg = qrGenerator.gen();
            byte[] qrImgRawBytes = IOUtils.toByteArray(qrImg.getInputStream());
            qrImg.close();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(qrImg.getMediaType());
            return new ResponseEntity<>(qrImgRawBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            LOGGER.error("When getting a QR", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/api/urls/{urlHash:[a-zA-Z0-9]+}/qrs")
    public ResponseEntity<byte[]> getAllQrs(@PathVariable String urlHash) {
        return null;
    }

    @PatchMapping(value = "/api/urls/{urlHash:[a-zA-Z0-9]+}/qrs/{qrId:[0-9]+}", consumes = MediaType
            .MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> patchExistingQr(@PathVariable String urlHash, @PathVariable int qrId,
                                                  @RequestPart(value = "bg", required = false) String bg,
                                                  @RequestPart(value = "fg", required = false) String fg,
                                                  @RequestPart(value = "logoImg", required = false) MultipartFile
                                                          logoImg) {
        try {
            Color bgColor = bg == null ? null : new Color(bg);
            Color fgColor = fg == null ? null : new Color(fg);
            Image image = logoImg == null ? null : new Image(logoImg.getInputStream(), MediaType.parseMediaType
                    (logoImg.getContentType()));
            QrGenerator qrGenerator = new QrQrCodeMonkey(urlHash, fgColor, bgColor, image);
            qrRepository.update(urlHash, qrId, qrGenerator);
            if (image != null) {
                image.getInputStream().close();
            }
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IOException e) {
            LOGGER.error("When patching a QR", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

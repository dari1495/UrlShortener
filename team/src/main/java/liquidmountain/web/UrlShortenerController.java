package liquidmountain.web;

import com.google.common.hash.Hashing;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import liquidmountain.domain.Click;
import liquidmountain.domain.ShortURL;
import liquidmountain.repository.ClickRepository;
import liquidmountain.repository.ShortURLRepository;
import liquidmountain.services.ExtractInfo;
import liquidmountain.services.GoogleSafeBrowsingUrlVerifier;
import liquidmountain.services.UrlValidatorAndCheckerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

/**
 * Clase con los endpoints de la aplicación
 */
public class UrlShortenerController {
	private static final Logger LOG = LoggerFactory
			.getLogger(UrlShortenerController.class);
	@Autowired
	protected ShortURLRepository shortURLRepository;

	@Autowired
	protected ClickRepository clickRepository;

	@Autowired
	protected ExtractInfo extractInfo;

	/**
	 * Endpoint para acortar URL's.
	 * @param id: String identificador de la url
	 * @param request: HttpServletRequest peticion del usuario
	 * @return ResponseEntity GONE/NOT_FOUND
	 */
    @ApiOperation(value ="Redirect")
    @RequestMapping(value = "/{id:[a-zA-Z0-9]+(?!\\.html)}", method = RequestMethod.GET)
    public ResponseEntity<?> redirectTo(@PathVariable String id, HttpServletRequest request,
                                        @RequestParam(value = "qr", required = false, defaultValue = "false") boolean
                                                comesFromQr) {
        ShortURL l = shortURLRepository.findByKey(id);
		if(l != null){
			// Construct date and time objects
			Calendar dateCal = Calendar.getInstance();
			dateCal.setTime(l.getExpirationDate());
			Calendar timeCal = Calendar.getInstance();
			timeCal.setTime(l.getExpirationTime());

			// Extract the time of the "time" object to the "date"
			dateCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
			dateCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
			dateCal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND));

			// Get the time value!
			java.util.Date exp = dateCal.getTime();

			Instant then = exp.toInstant();
			Instant now = ZonedDateTime.now().toInstant();

			if (now.isBefore(then)) {
				ExtractInfo ex = new ExtractInfo();
				createAndSaveClick(id, ex.extractAll(request), comesFromQr);
				return createSuccessfulRedirectToResponse(l);
			} else {
				LOG.info("Requested link has expired. Returning " + HttpStatus.GONE);
				HttpHeaders h = new HttpHeaders();
				String own = request.getRequestURL().toString();
				String normal = own.substring(0, own.indexOf(l.getHash()));
				h.setLocation(URI.create(normal + "exp.html"));
				return new ResponseEntity<>(h, HttpStatus.valueOf(l.getMode()));
			}
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	/**
	 * Metodo para crear y guardar clikc's
	 * @param hash: String identificador hash de la URI
	 * @param info:
	 *            0: browser
	 *            1: country
	 *            2: IP
	 *            3: OS
	 *            4: referrer
	 */
	private void createAndSaveClick(String hash, String[] info, boolean comesFromQr) {
		Click cl = new Click(null, hash, new Date(System.currentTimeMillis()),
				info[4], info[0], info[3], info[2], info[1], comesFromQr);
		cl=clickRepository.save(cl);
		System.out.println(info[0] + " " + info[3] + " " + info[1]);
		LOG.info(cl!=null?"["+hash+"] saved with id ["+cl.getId()+"]":"["+hash+"] was not saved");
	}

	/**
	 * @param l: ShortURL url acortada a la cual se redirigira
	 * @return ResponseEntity OK
	 */
	private ResponseEntity<?> createSuccessfulRedirectToResponse(ShortURL l) {
		HttpHeaders h = new HttpHeaders();
		h.setLocation(URI.create(l.getTarget()));
		return new ResponseEntity<>(h, HttpStatus.valueOf(l.getMode()));
	}

	/**
	 * Endpoint to verify that a link is well formed and is valid
	 * @param url: String a verificar
	 * @param request: HttpServletRequest user request
	 * @return ResponseEntity: SAFE/UNSAFE url
	 */
    @ApiOperation(value ="Verify that a link is well formed and is valid")
	@RequestMapping(value = "/api/verify", method = RequestMethod.POST)
	public ResponseEntity<String> verify(@RequestParam("url") String url, HttpServletRequest request) {
		UrlValidatorAndCheckerImpl urlValidatorAndChecker = new UrlValidatorAndCheckerImpl(url);
		HttpHeaders h = new HttpHeaders();
		if(urlValidatorAndChecker.execute()){
			return new ResponseEntity<>("SAFE", h, HttpStatus.OK);
		} else return new ResponseEntity<>("UNSAFE", h, HttpStatus.OK);
	}

	/**
	 * Endpoint to check link is safe
	 * @param url: String que se comprobara si es segura
	 * @param request: HttpServletRequest
	 * @return ResponseEntity: SAFE/UNSAFE url
	 */
    @ApiOperation(value ="Check link is safe")
	@RequestMapping(value = "/api/safe", method = RequestMethod.POST)
	public ResponseEntity<String> checkSafe(@RequestParam("url") String url, HttpServletRequest request) {
		GoogleSafeBrowsingUrlVerifier gSafe = new GoogleSafeBrowsingUrlVerifier();
		HttpHeaders  h = new HttpHeaders();
		System.out.println(gSafe.isSafe("http://www.google.es"));
		if(gSafe.isSafe(url)){
			return new ResponseEntity<>("SAFE", h, HttpStatus.OK);
		} else return new ResponseEntity<>("UNSAFE", h, HttpStatus.OK);
	}

	/**
	 * Endpoint to short and save link
	 * @param url: String url para acortar
	 * @param date: String fecha que caducara
	 * @param time: String hora del dia que caducara
	 * @param sponsor: String
	 * @param request: HttpServletRequest
	 * @return ResponseEntity CREATED/BAD_REQUEST
	 */
    @ApiOperation(value ="Short and save one link")
	@RequestMapping(value = "/api/urls", method = RequestMethod.POST)
	public ResponseEntity<ShortURL> shortener(@RequestParam("url") String url,
											  @RequestParam("date") String date,
											  @RequestParam("time") String time,
											  @RequestParam(value = "sponsor", required = false) String sponsor,
											  HttpServletRequest request) {
		ExtractInfo ex = new ExtractInfo();
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		DateFormat sdft = new SimpleDateFormat("HH:mm");
		Date d = new Date(System.currentTimeMillis());
		Time t = new Time(System.currentTimeMillis());
		if(date.equals("")) {
			d = null;
		} else {
			try {
				d.setTime(sdf.parse(date).getTime());
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		if(time.equals("")) {
			t = null;
		} else try {
			t.setTime(sdft.parse(time).getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}

		ShortURL su = createAndSaveIfValid(url, sponsor, UUID
				.randomUUID().toString(), ex.extractIP(request), d, t);
		if (su != null) {
			HttpHeaders h = new HttpHeaders();
			h.setLocation(su.getUri());
			return new ResponseEntity<>(su, h, HttpStatus.CREATED);
		} else {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 *  Método privado que crear y guarda una url en caso de que este bien formada y sea valida
	 * @param url: String URI que se guarda y comprueba
	 * @param sponsor: String
	 * @param owner: String
	 * @param ip: String direccion ip desde donde se genera
	 * @param expirationDate: String fecha que caduca
	 * @param expirationTime: String hora que caduca
	 * @return objeto ShortURL, con la uri acortada / null en el caso de que falle
	 */
	private ShortURL createAndSaveIfValid(String url, String sponsor,
										  String owner, String ip, Date expirationDate, Time expirationTime) {
		GoogleSafeBrowsingUrlVerifier googleSafe = new GoogleSafeBrowsingUrlVerifier();
		boolean isSafe = googleSafe.isSafe(url);
		UUID uuid = UUID.randomUUID();

		UrlValidatorAndCheckerImpl urlValidatorAndChecker = new UrlValidatorAndCheckerImpl(url);
		if (urlValidatorAndChecker.execute()) {
//			String id = Hashing.murmur3_32()
//					.hashString(url, StandardCharsets.UTF_8).toString();
			String id = Hashing.murmur3_32()
					.hashString(uuid.toString(), StandardCharsets.UTF_8).toString();
			ShortURL su = null;
			try {
				su = new ShortURL(id, url, new URI("http://localhost:8080/" + id), sponsor, new Date(
                        System.currentTimeMillis()), owner,
                        HttpStatus.TEMPORARY_REDIRECT.value(), true, ip, null, expirationDate, expirationTime);
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			ShortURL old = shortURLRepository.findByKey(su.getHash());
			if(old != null){
				shortURLRepository.update(su);
				return su;
			} return shortURLRepository.save(su);
		} else {
			return null;
		}
	}
}
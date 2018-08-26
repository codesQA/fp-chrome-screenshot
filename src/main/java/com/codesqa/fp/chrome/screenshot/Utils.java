package com.codesqa.fp.chrome.screenshot;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import sun.misc.BASE64Decoder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

import javax.imageio.ImageIO;

@SuppressWarnings("restriction")
public class Utils {

	static WebSocket webSocket = null;
	static ChromeDriverService service;
	final static Object waitCoordinator = new Object();
	final static int timeoutValue = 5;
	public static String response;

	static DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy_h-m-s");

	protected static String getWebSocketDebuggerUrl() throws IOException {
		String webSocketDebuggerURL = "";
		File file = new File(System.getProperty("user.dir") + "/target/chromedriver.log");
		try {

			Scanner sc = new Scanner(file);
			String urlString = "";
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (line.contains("DevTools request: http://localhost")) {
					urlString = line.substring(line.indexOf("http"), line.length()).replace("/version", "");
					break;
				}
			}
			sc.close();

			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String json = org.apache.commons.io.IOUtils.toString(reader);
			JSONArray jsonArray = new JSONArray(json);
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				if (jsonObject.getString("type").equals("page")) {
					webSocketDebuggerURL = jsonObject.getString("webSocketDebuggerUrl");
					break;
				}
			}
		} catch (FileNotFoundException e) {
			throw e;
		}
		if (webSocketDebuggerURL.equals(""))
			throw new RuntimeException("webSocketDebuggerURL not found..");
		return webSocketDebuggerURL;
	}

	private static String sendWSMessage(String url, String message)
			throws IOException, WebSocketException, InterruptedException {

		final int matchJSONId = new JSONObject(message).getInt("id");
		if (webSocket == null) {
			webSocket = new WebSocketFactory().createSocket(url).addListener(new WebSocketAdapter() {
				@Override
				public void onTextMessage(WebSocket ws, String message) {
					response = message;
					// Received response.
					if (new JSONObject(message).getInt("id") == matchJSONId) {
						synchronized (waitCoordinator) {
							waitCoordinator.notifyAll();
						}
					}
				}
			}).connect();
		}
		webSocket.sendText(message);
		synchronized (waitCoordinator) {
			waitCoordinator.wait(timeoutValue * 1000);
		}
		return response;
	}

	protected static String getDeviceMetrics(String wsURL)
			throws IOException, WebSocketException, InterruptedException {
		String msg = "{\"id\":0,\"method\" : \"Runtime.evaluate\", \"params\" : {\"returnByValue\" : true, \"expression\" : \"({width: Math.max(window.innerWidth,document.body.scrollWidth,document.documentElement.scrollWidth)|0,height: Math.max(window.innerHeight,document.body.scrollHeight,document.documentElement.scrollHeight)|0,deviceScaleFactor: window.devicePixelRatio || 1,mobile: typeof window.orientation !== 'undefined'})\"}}";
		JSONObject responseParser = new JSONObject(sendWSMessage(wsURL, msg));
		JSONObject result1Parser = responseParser.getJSONObject("result");
		JSONObject result2Parser = result1Parser.getJSONObject("result");
		return result2Parser.getJSONObject("value").toString();
	}

	protected static void setDeviceMetrics(String wsURL, String devicePropertiesJSON)
			throws IOException, WebSocketException, InterruptedException {
		String msg = "{\"id\":1,\"method\":\"Emulation.setDeviceMetricsOverride\", \"params\":" + devicePropertiesJSON
				+ "}";
		sendWSMessage(wsURL, msg);
	}

	protected static String getbase64ScreenShotData(String wsURL)
			throws IOException, WebSocketException, InterruptedException {
		String msg = "{\"id\":2,\"method\":\"Page.captureScreenshot\", \"params\":{\"format\":\"png\", \"fromSurface\":true}}";
		JSONObject responseParser = new JSONObject(sendWSMessage(wsURL, msg));
		JSONObject resultParser = responseParser.getJSONObject("result");
		return resultParser.getString("data");
	}

	protected static void clearDeviceMetrics(String wsURL)
			throws IOException, WebSocketException, InterruptedException {
		String msg = "{\"id\":3,\"method\":\"Emulation.clearDeviceMetricsOverride\", \"params\":{}}";
		sendWSMessage(wsURL, msg);
	}

	protected static WebDriver initializeDriver(WebDriver driver) throws IOException {
		WebDriver wd;
		String pathToChromedriver = System.getProperty("user.dir") + File.separator + "drivers" + File.separator
				+ "chromedriver-windows-32bit.exe";
		System.setProperty("webdriver.chrome.driver", pathToChromedriver);

		ChromeOptions options = new ChromeOptions();
		options.addArguments("disable-infobars");
		options.addArguments("--start-maximized");
		options.setExperimentalOption("useAutomationExtension", false);

		DesiredCapabilities capabilities = DesiredCapabilities.chrome();
		capabilities.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
		capabilities.setCapability(ChromeOptions.CAPABILITY, options);

		System.setProperty(ChromeDriverService.CHROME_DRIVER_LOG_PROPERTY,
				System.getProperty("user.dir") + File.separator + "/target/chromedriver.log");
		System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, pathToChromedriver);
		service = new ChromeDriverService.Builder().usingAnyFreePort().withVerbose(true).build();
		service.start();

		wd = new RemoteWebDriver(service.getUrl(), capabilities);
		return wd;
	}

	protected static void takeScreenShot() throws IOException, WebSocketException, InterruptedException {
		String webSocketURL = getWebSocketDebuggerUrl();
		System.out.println("Web Socket Debug URL: " + webSocketURL);

		String deviceJson = getDeviceMetrics(webSocketURL);
		setDeviceMetrics(webSocketURL, deviceJson);
		String base64Data = getbase64ScreenShotData(webSocketURL);
		clearDeviceMetrics(webSocketURL);

		Date date = new Date();
		File ScreenShotDestFile = new File(
				System.getProperty("user.dir") + File.separator + dateFormat.format(date) + ".png");

		BASE64Decoder base64Decoder = new BASE64Decoder();
		byte[] decodedBytes = base64Decoder.decodeBuffer(base64Data);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(decodedBytes));

		ImageIO.write(image, "png", ScreenShotDestFile);
	}

}

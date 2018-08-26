package com.codesqa.fp.chrome.screenshot;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.neovisionaries.ws.client.WebSocketException;

public class FullPageScreenshot {

	private static WebDriver driver;

	DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy_h-m-s");

	@BeforeTest
	public void setUp() throws IOException {
		driver = Utils.initializeDriver(driver);
		driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
		driver.get("https://www.wikipedia.org/");
	}

	@Test
	public void takeScreenshot() throws IOException, InterruptedException, WebSocketException {
		Utils.takeScreenShot();
		WebElement searchInput = driver.findElement(By.id("searchInput"));
		WebElement searchButton = driver.findElement(By.xpath("//*[@id='search-form']/fieldset/button"));
		searchInput.sendKeys("Selenium_(software)");
		Utils.takeScreenShot();
		searchButton.click();
		Utils.takeScreenShot();
	}

	@AfterTest
	public void tearDown() {
		Utils.webSocket.disconnect();
		driver.close();
		driver.quit();
		Utils.service.stop();
	}

}

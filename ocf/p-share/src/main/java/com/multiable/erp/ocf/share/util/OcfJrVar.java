package com.multiable.erp.ocf.share.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

import com.multiable.core.share.app.AppManager;

public class OcfJrVar {

	public ByteArrayInputStream getLargeBackground() {
		String appPath = AppManager.getAppPath("ce01_ocf");
		String rootDir = appPath + "/card/" + "pic01.jpg";

		rootDir = rootDir.replace("\\", "/");

		String urlString = "vfs:" + rootDir;
		urlString = urlString.replaceAll(" ", "%20");

		URI uri;
		try {
			uri = new URI(urlString);
			URLConnection conn = uri.toURL().openConnection();

			conn.setUseCaches(false);

			InputStream is = conn.getInputStream();

			// Convert inputStream into byte[]
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			int bytesRead = 0;
			byte[] buf = new byte[is.available()];
			while ((bytesRead = is.read(buf)) != -1) {
				bao.write(buf, 0, bytesRead);
			}

			byte[] data = bao.toByteArray();

			ByteArrayInputStream bs = new ByteArrayInputStream(data);

			return bs;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Test code to make
		// boolean test = true;

		// if (test) {
		// CawJrVar cawjar = new CawJrVar();
		// return cawjar.getImage("feBVb7l3649047590991O12", 1);
		// }

		return null;

	}

	public ByteArrayInputStream getSmallBackground() {
		String appPath = AppManager.getAppPath("ce01_ocf");
		String rootDir = appPath + "/card/" + "pic02.jpg";

		rootDir = rootDir.replace("\\", "/");

		String urlString = "vfs:" + rootDir;
		urlString = urlString.replaceAll(" ", "%20");

		URI uri;
		try {
			uri = new URI(urlString);
			URLConnection conn = uri.toURL().openConnection();

			conn.setUseCaches(false);

			InputStream is = conn.getInputStream();

			// Convert inputStream into byte[]
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			int bytesRead = 0;
			byte[] buf = new byte[is.available()];
			while ((bytesRead = is.read(buf)) != -1) {
				bao.write(buf, 0, bytesRead);
			}

			byte[] data = bao.toByteArray();

			ByteArrayInputStream bs = new ByteArrayInputStream(data);

			return bs;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}
}

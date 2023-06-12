package com.github.pohtml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Supplier;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class HtmlMethod<T extends Get2> extends HttpServlet implements Supplier<T> {
	
	private static final long serialVersionUID = 1L;

	private final long lastModified;
	
	public HtmlMethod(long lastModified) {
		this.lastModified = lastModified;
	}

	protected abstract void doModel(String uri, HttpServletRequest request, HttpServletResponse response) throws Exception;

	final void doIt(HttpServletRequest request, HttpServletResponse response) {
		try {
			String uri = request.getRequestURI();
			WebServlet annotation = getClass().getAnnotation(WebServlet.class);
			String context = annotation.value()[1];
			if (uri.endsWith(".html")) {
				doView(context, request, response);
			} else {
				doModel(context, request, response);
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	void doView(String uri, HttpServletRequest req, HttpServletResponse resp) throws Exception {
		URL url = new URL(AbstractStaticContext.BASE + uri);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		Enumeration<String> headerNames = req.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			if (!AbstractStaticContext.REQUEST_HEADERS.contains(headerName.toLowerCase())) {
				continue;
			}
			Enumeration<String> headerValues = req.getHeaders(headerName);
			while (headerValues.hasMoreElements()) {
				String headerValue = headerValues.nextElement();
				connection.setRequestProperty(headerName, headerValue);
			}
		}
		int responseCode = connection.getResponseCode();
		resp.setStatus(responseCode);
		if (responseCode == 200) {
			resp.addHeader("cache-control", "max-age=0, must-revalidate");	
		}
		for (Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
			String key = entry.getKey();
			if (key == null || !AbstractStaticContext.RESPONSE_HEADERS.contains(key.toLowerCase())) {
				continue;
			}
			for (String value : entry.getValue()) {
				resp.addHeader(key, value);
			}
		}
		try (InputStream reader = getInputStream(connection); OutputStream writer = resp.getOutputStream()) {
			int read = reader.read();
			if (read == -1) {
				return;
			}
			byte[] buffer = new byte[1024];
			buffer[0] = (byte) read;
			read = reader.read(buffer, 1, buffer.length - 1) + 1;
			if (responseCode == 200) {
				String start = new String(Arrays.copyOfRange(buffer, 0, 256));
				String head = "<head>";
				int index = start.indexOf(head);
				if (index == -1) {
					index = start.indexOf("<HEAD>");
				}
				byte[] base = ("<base href='" + getHtmlBase(url.toString()) + "/'>").getBytes();
				if (index == -1) {
					writer.write(base);
				} else {
					index += head.length();
					writer.write(buffer, 0, index);
					writer.write(base);
					writer.write(buffer, index, read - index);
				}
			} else {
				writer.write(buffer, 0, read);
			}
			read = reader.read(buffer);
			while (read != -1) {
				writer.write(buffer, 0, read);
				read = reader.read(buffer);
			}
		}

	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			String uri = request.getRequestURI();
			if (uri.endsWith(".html")) {
				doView(uri, request, response);
			} else {
				doModel(uri, request, response);
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private InputStream getInputStream(HttpURLConnection connection) {
		try {
			return connection.getInputStream();
		} catch (IOException e) {
			return connection.getErrorStream();
		}
	}

	private String getHtmlBase(String uri) {
		return uri.substring(0, uri.lastIndexOf("/"));
	}
	
}

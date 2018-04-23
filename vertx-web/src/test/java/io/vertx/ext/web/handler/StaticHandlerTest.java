/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.web.handler;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.Http2PushMapping;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.impl.Utils;
import io.vertx.ext.web.WebTestBase;
import org.junit.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.text.DateFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StaticHandlerTest extends WebTestBase {

  private final DateFormat dateTimeFormatter = Utils.createRFC1123DateTimeFormatter();

  protected StaticHandler stat;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    stat = StaticHandler.create();
    router.route().handler(stat);
  }

  @Test
  public void testGetDefaultIndex() throws Exception {
    testRequest(HttpMethod.GET, "/", 200, "OK", "<html><body>Index page</body></html>");
  }

  @Test
  public void testGetSubdirectorySlashDefaultIndex() throws Exception {
    testRequest(HttpMethod.GET, "/somedir/", 200, "OK", "<html><body>Subdirectory index page</body></html>");
  }

  @Test
  public void testGetOtherIndex() throws Exception {
    stat.setIndexPage("otherpage.html");
    testRequest(HttpMethod.GET, "/", 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testGetSubdirectoryOtherIndex() throws Exception {
    stat.setIndexPage("otherpage.html");
    testRequest(HttpMethod.GET, "/somedir", 200, "OK", "<html><body>Subdirectory other page</body></html>");
  }

  @Test
  public void testGetSubdirectorySlashOtherIndex() throws Exception {
    stat.setIndexPage("otherpage.html");
    testRequest(HttpMethod.GET, "/somedir", 200, "OK", "<html><body>Subdirectory other page</body></html>");
  }

  @Test
  public void testGetFileWithSpaces() throws Exception {
    testRequest(HttpMethod.GET, "/file%20with%20spaces.html", 200, "OK", "<html><body>File with spaces</body></html>");
  }

  @Test
  public void testGetOtherPage() throws Exception {
    testRequest(HttpMethod.GET, "/otherpage.html", 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testGetPageFromSubdir() throws Exception {
    testRequest(HttpMethod.GET, "/somedir/something.html", 200, "OK", "<html><body>Blah page</body></html>");
  }

  @Test
  public void testBadPathNoLeadingSlash() throws Exception {
    testRequest(HttpMethod.GET, "otherpage.html", 404, "Not Found");
  }

  @Test
  public void testGetHiddenPage() throws Exception {
    testRequest(HttpMethod.GET, "/.hidden.html", 200, "OK", "<html><body>Hidden page</body></html>");
  }

  @Test
  public void testCantGetHiddenPage() throws Exception {
    stat.setIncludeHidden(false);
    testRequest(HttpMethod.GET, "/.hidden.html", 404, "Not Found");
  }

  @Test
  public void testGetHiddenPageSubdir() throws Exception {
    testRequest(HttpMethod.GET, "/somedir/.hidden.html", 200, "OK", "<html><body>Hidden page</body></html>");
  }

  @Test
  public void testCantGetHiddenPageSubdir() throws Exception {
    stat.setIncludeHidden(false);
    testRequest(HttpMethod.GET, "/somedir/.hidden.html", 404, "Not Found");
  }

  @Test
  public void testCantGetNoSuchPage() throws Exception {
    testRequest(HttpMethod.GET, "/notexists.html", 404, "Not Found");
  }

  @Test
  public void testCantGetNoSuchPageInSubDir() throws Exception {
    testRequest(HttpMethod.GET, "/somedir/notexists.html", 404, "Not Found");
  }

  @Test
  public void testDateHeaderSet() throws Exception {
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String dateHeader = res.headers().get("date");
      assertNotNull(dateHeader);
      long diff = System.currentTimeMillis() - toDateTime(dateHeader);
      assertTrue(diff > 0 && diff < 2000);
    }, 200, "OK", null);
  }

  @Test
  public void testContentHeadersSet() throws Exception {
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String contentType = res.headers().get("content-type");
      String contentLength = res.headers().get("content-length");
      assertEquals("text/html;charset=UTF-8", contentType);
      assertEquals(fileSize("src/test/resources/webroot/otherpage.html"), Integer.valueOf(contentLength).intValue());
    }, 200, "OK", null);
    testRequest(HttpMethod.GET, "/foo.json", null, res -> {
      String contentType = res.headers().get("content-type");
      String contentLength = res.headers().get("content-length");
      assertEquals("application/json", contentType);
      assertEquals(fileSize("src/test/resources/webroot/foo.json"), Integer.valueOf(contentLength).intValue());
    }, 200, "OK", null);
  }

  @Test
  public void testNoLinkPreload() throws Exception {
    stat.setWebRoot("webroot/somedir3");
    testRequest(HttpMethod.GET, "/testLinkPreload.html", null, res -> {
      List<String> linkHeaders = res.headers().getAll("Link");
      assertTrue(linkHeaders.isEmpty());
    }, 200, "OK", null);
  }

  @Test
  public void testLinkPreload() throws Exception {
    List<Http2PushMapping> mappings = new ArrayList<>();
    mappings.add(new Http2PushMapping("style.css", "style", false));
    mappings.add(new Http2PushMapping("coin.png", "image", false));
    stat.setHttp2PushMapping(mappings)
        .setWebRoot("webroot/somedir3");
    testRequest(HttpMethod.GET, "/testLinkPreload.html", null, res -> {
      List<String> linkHeaders = res.headers().getAll("Link");
      assertTrue(linkHeaders.contains("<style.css>; rel=preload; as=style"));
      assertTrue(linkHeaders.contains("<coin.png>; rel=preload; as=image"));
    }, 200, "OK", null);
  }

  @Test
  public void testNoHttp2Push() throws Exception {
    stat.setWebRoot("webroot/somedir3");
    router.route().handler(stat);
    HttpServer http2Server = vertx.createHttpServer(new HttpServerOptions()
      .setUseAlpn(true)
      .setSsl(true)
      .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert.pem")));
    http2Server.requestHandler(router).listen(8443);

    HttpClientOptions options = new HttpClientOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setPemTrustOptions(new PemTrustOptions().addCertPath("tls/server-cert.pem"));
    HttpClient client = vertx.createHttpClient(options);
    HttpClientRequest request = client.get(8443, "localhost", "/testLinkPreload.html", resp -> {
      assertEquals(200, resp.statusCode());
      assertEquals(HttpVersion.HTTP_2, resp.version());
      resp.bodyHandler(this::assertNotNull);
      testComplete();
    });
    request.pushHandler(pushedReq -> pushedReq.handler(pushedResp -> {
      fail();
    }));
    request.end();
    await();
  }

  @Test
  public void testHttp2Push() throws Exception {
    List<Http2PushMapping> mappings = new ArrayList<>();
    mappings.add(new Http2PushMapping("style.css", "style", false));
    mappings.add(new Http2PushMapping("coin.png", "image", false));
    stat.setHttp2PushMapping(mappings)
        .setWebRoot("webroot/somedir3");
    router.route().handler(stat);
    HttpServer http2Server = vertx.createHttpServer(new HttpServerOptions()
        .setUseAlpn(true)
        .setSsl(true)
        .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert.pem")));
    http2Server.requestHandler(router).listen(8443);

    HttpClientOptions options = new HttpClientOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setPemTrustOptions(new PemTrustOptions().addCertPath("tls/server-cert.pem"));
    HttpClient client = vertx.createHttpClient(options);
    HttpClientRequest request = client.get(8443, "localhost", "/testLinkPreload.html", resp -> {
      assertEquals(200, resp.statusCode());
      assertEquals(HttpVersion.HTTP_2, resp.version());
      resp.bodyHandler(this::assertNotNull);
    });
    CountDownLatch latch = new CountDownLatch(2);
    request.pushHandler(pushedReq -> pushedReq.handler(pushedResp -> {
      assertNotNull(pushedResp);
      pushedResp.bodyHandler(this::assertNotNull);
      latch.countDown();
    }));
    request.end();
    latch.await();
  }

  @Test
  public void testSkipCompressionForMediaTypes() throws Exception {
    Set<String> compressedMediaTypes = new HashSet<>();
    compressedMediaTypes.add("image/jpeg");
    stat.skipCompressionForMediaTypes(compressedMediaTypes);
    router.route().handler(stat);

    waitFor(4);
    server.close();
    server = vertx.createHttpServer(getHttpServerOptions().setCompressionSupported(true));
    server.requestHandler(router).listen(8444);

    final List<CharSequence> acceptEncodings = new ArrayList<CharSequence>();
    acceptEncodings.add("gzip");
    acceptEncodings.add("jpg");
    acceptEncodings.add("jpeg");
    acceptEncodings.add("png");
    final String[] routes = {"/testCompressionSuffix.html", "/somedir/range.jpg", "/somedir/range.jpeg", "/somedir3/coin.png"};
    final int[] statusCodes = new int[4];
    final String[] contentEncodings = new String[4];

    for (int i = 0; i < 4; i++) {
      int finalI = i;
      HttpClientRequest request = client.get(8444, "localhost", routes[i], resp -> {
        statusCodes[finalI] = resp.statusCode();
        contentEncodings[finalI] = resp.getHeader(HttpHeaders.CONTENT_ENCODING);
        complete();
      });
      request.putHeader(HttpHeaders.ACCEPT_ENCODING, acceptEncodings);
      request.end();
    }
    await();

    final int successCode = 200;
    //jpeg and jpg have the same mime type, hence both are not compressed
    final CharSequence[] expectedContentEncodings = {"gzip", HttpHeaders.IDENTITY, HttpHeaders.IDENTITY, "gzip"};
    for (int i = 0; i < 4; i++) {
      assertEquals("Status code does not equal 200 for request " + (i+1), successCode, statusCodes[i]);
      assertEquals("Content-Encoding did not match for request " + (i+1), expectedContentEncodings[i].toString(), contentEncodings[i]);
    }
  }

  @Test
  public void testSkipCompressionForSuffixes() throws Exception {
    Set<String> compressedSuffixes = new HashSet<>();
    compressedSuffixes.add("jpg");
    stat.skipCompressionForSuffixes(compressedSuffixes);
    router.route().handler(stat);

    waitFor(4);
    server.close();
    server = vertx.createHttpServer(getHttpServerOptions().setCompressionSupported(true));
    server.requestHandler(router).listen(8444);

    final List<CharSequence> acceptEncodings = new ArrayList<CharSequence>();
    acceptEncodings.add("gzip");
    acceptEncodings.add("jpg");
    acceptEncodings.add("jpeg");
    acceptEncodings.add("png");
    final String[] routes = {"/testCompressionSuffix.html", "/somedir/range.jpg", "/somedir/range.jpeg", "/somedir3/coin.png"};
    final int[] statusCodes = new int[4];
    final String[] contentEncodings = new String[4];

    for (int i = 0; i < 4; i++) {
      int finalI = i;
      HttpClientRequest request = client.get(8444, "localhost", routes[i], resp -> {
        statusCodes[finalI] = resp.statusCode();
        contentEncodings[finalI] = resp.getHeader(HttpHeaders.CONTENT_ENCODING);
        complete();
      });
      request.putHeader(HttpHeaders.ACCEPT_ENCODING, acceptEncodings);
      request.end();
    }
    await();

    final int successCode = 200;
    final CharSequence[] expectedContentEncodings = {"gzip", HttpHeaders.IDENTITY, "gzip", "gzip"};
    for (int i = 0; i < 4; i++) {
      assertEquals("Status code does not equal 200 for request " + (i+1), successCode, statusCodes[i]);
      assertEquals("Content-Encoding did not match for request " + (i+1), expectedContentEncodings[i].toString(), contentEncodings[i]);
    }
  }

  @Test
  public void testHead() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    testRequest(HttpMethod.HEAD, "/otherpage.html", null, res -> {
      res.bodyHandler(buff -> assertEquals(0, buff.length()));
      res.endHandler(v -> latch.countDown());
    }, 200, "OK", null);
    awaitLatch(latch);
  }

  @Test
  public void testCacheReturnFromCache() throws Exception {
    AtomicReference<String> lastModifiedRef = new AtomicReference<>();
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String cacheControl = res.headers().get("cache-control");
      String lastModified = res.headers().get("last-modified");
      lastModifiedRef.set(lastModified);
      assertNotNull(cacheControl);
      assertNotNull(lastModified);
      assertEquals("public, max-age=" + StaticHandler.DEFAULT_MAX_AGE_SECONDS, cacheControl);
    }, 200, "OK", "<html><body>Other page</body></html>");
    testRequest(HttpMethod.GET, "/otherpage.html", req -> req.putHeader("if-modified-since", lastModifiedRef.get()), null, 304, "Not Modified", null);
  }

  @Test
  public void testCacheIndexPageReturnFromCache() throws Exception {
    AtomicReference<String> lastModifiedRef = new AtomicReference<>();
    testRequest(HttpMethod.GET, "/somedir", null, res -> {
      String cacheControl = res.headers().get("cache-control");
      String lastModified = res.headers().get("last-modified");
      lastModifiedRef.set(lastModified);
      assertNotNull(cacheControl);
      assertNotNull(lastModified);
      assertEquals("public, max-age=" + StaticHandler.DEFAULT_MAX_AGE_SECONDS, cacheControl);
    }, 200, "OK", "<html><body>Subdirectory index page</body></html>");
    testRequest(HttpMethod.GET, "/somedir", req -> req.putHeader("if-modified-since", lastModifiedRef.get()), null, 304, "Not Modified", null);
  }

  @Test
  public void testCachingDisabled() throws Exception {
    stat.setCachingEnabled(false);
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String cacheControl = res.headers().get("cache-control");
      String lastModified = res.headers().get("last-modified");
      assertNull(cacheControl);
      assertNull(lastModified);
    }, 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testCacheNoCacheAsNoIfModifiedSinceHeader() throws Exception {
    testRequest(HttpMethod.GET, "/otherpage.html", 200, "OK", "<html><body>Other page</body></html>");
    testRequest(HttpMethod.GET, "/otherpage.html", 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testCacheGetNew() throws Exception {
    AtomicReference<String> lastModifiedRef = new AtomicReference<>();
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String cacheControl = res.headers().get("cache-control");
      String lastModified = res.headers().get("last-modified");
      lastModifiedRef.set(lastModified);
      assertNotNull(cacheControl);
      assertNotNull(lastModified);
      assertEquals("public, max-age=" + StaticHandler.DEFAULT_MAX_AGE_SECONDS, cacheControl);
    }, 200, "OK", "<html><body>Other page</body></html>");
    testRequest(HttpMethod.GET, "/otherpage.html", req -> req.putHeader("if-modified-since", dateTimeFormatter.format(toDateTime(lastModifiedRef.get()) - 1)), res -> {
    }, 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testSendVaryAcceptEncodingHeader() throws Exception {
    testRequest(HttpMethod.GET, "/otherpage.html", req -> req.putHeader("accept-encoding", "gzip"), res -> {
      String vary = res.headers().get("vary");
      assertNotNull(vary);
      assertEquals("accept-encoding", vary);
    }, 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testNoSendingOfVaryAcceptEncodingHeader() throws Exception {
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String vary = res.headers().get("vary");
      assertNull(vary);
    }, 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testSetMaxAge() throws Exception {
    long maxAge = 60 * 60;
    stat.setMaxAgeSeconds(maxAge);
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String cacheControl = res.headers().get("cache-control");
      assertEquals("public, max-age=" + maxAge, cacheControl);
    }, 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testGetOtherPageTwice() throws Exception {
    testRequest(HttpMethod.GET, "/otherpage.html", 200, "OK", "<html><body>Other page</body></html>");
    testRequest(HttpMethod.GET, "/otherpage.html", 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testServeFilesFromFilesystem() throws Exception {
    stat.setWebRoot("src/test/filesystemwebroot");
    testRequest(HttpMethod.GET, "/fspage.html", 200, "OK", "<html><body>File system page</body></html>");
  }

  @Test
  public void testServeFilesFromFilesystemWebRootConstructor() throws Exception {
    stat = StaticHandler.create("src/test/filesystemwebroot");
    router.clear();
    router.route().handler(stat);
    testRequest(HttpMethod.GET, "/fspage.html", 200, "OK", "<html><body>File system page</body></html>");
  }

  @Test
  public void testCacheFilesNotReadOnly() throws Exception {
    stat.setFilesReadOnly(false);
    stat.setWebRoot("src/test/filesystemwebroot");
    long modified = Utils.secondsFactor(new File("src/test/filesystemwebroot", "fspage.html").lastModified());
    testRequest(HttpMethod.GET, "/fspage.html", null, res -> {
      String lastModified = res.headers().get("last-modified");
      assertEquals(modified, toDateTime(lastModified));
    }, 200, "OK", "<html><body>File system page</body></html>");
    testRequest(HttpMethod.GET, "/fspage.html", req -> req.putHeader("if-modified-since", dateTimeFormatter.format(modified)), null, 304, "Not Modified", null);
  }

  @Test
  public void testCacheFilesEntryCached() throws Exception {
    stat.setFilesReadOnly(false);
    stat.setWebRoot("src/test/filesystemwebroot");
    File resource = new File("src/test/filesystemwebroot", "fspage.html");
    long modified = resource.lastModified();
    testRequest(HttpMethod.GET, "/fspage.html", null, res -> {
      String lastModified = res.headers().get("last-modified");
      assertEquals(modified, toDateTime(lastModified));
      // Now update the web resource
      resource.setLastModified(modified + 1000);
    }, 200, "OK", "<html><body>File system page</body></html>");
    // But it should still return not modified as the entry is cached
    testRequest(HttpMethod.GET, "/fspage.html", req -> req.putHeader("if-modified-since", dateTimeFormatter.format(modified)), null, 304, "Not Modified", null);
  }

  @Test
  public void testCacheFilesEntryOld() throws Exception {
    stat.setFilesReadOnly(false);
    stat.setWebRoot("src/test/filesystemwebroot");
    stat.setCacheEntryTimeout(2000);
    File resource = new File("src/test/filesystemwebroot", "fspage.html");
    long modified = Utils.secondsFactor(resource.lastModified());
    testRequest(HttpMethod.GET, "/fspage.html", null, res -> {
      String lastModified = res.headers().get("last-modified");
      assertEquals(modified, toDateTime(lastModified));
      // Now update the web resource
      resource.setLastModified(modified + 1000);
    }, 200, "OK", "<html><body>File system page</body></html>");
    // But it should return a new entry as the entry is now old
    Thread.sleep(2001);
    testRequest(HttpMethod.GET, "/fspage.html", req -> req.putHeader("if-modified-since", dateTimeFormatter.format(modified)), res -> {
      String lastModified = res.headers().get("last-modified");
      assertEquals(modified + 1000, toDateTime(lastModified));
    }, 200, "OK", "<html><body>File system page</body></html>");
  }

  @Test
  public void testDirectoryListingText() throws Exception {
    stat.setDirectoryListing(true);
    Set<String> expected = new HashSet<>(Arrays.asList(".hidden.html", "a", "foo.json", "index.html", "otherpage.html", "somedir", "somedir2", "somedir3", "testCompressionSuffix.html", "file with spaces.html"));
    testRequest(HttpMethod.GET, "/", null, resp -> {
      resp.bodyHandler(buff -> {
        String sBuff = buff.toString();
        String[] elems = sBuff.split("\n");
        assertEquals(expected.size(), elems.length);
        for (String elem : elems) {
          assertTrue(expected.contains(elem));
        }
      });
    }, 200, "OK", null);
  }

  @Test
  public void testDirectoryListingTextNoHidden() throws Exception {
    stat.setDirectoryListing(true);
    stat.setIncludeHidden(false);
    Set<String> expected = new HashSet<>(Arrays.asList("foo.json", "a", "index.html", "otherpage.html", "somedir", "somedir2", "somedir3", "testCompressionSuffix.html", "file with spaces.html"));
    testRequest(HttpMethod.GET, "/", null, resp -> {
      resp.bodyHandler(buff -> {
        assertEquals("text/plain", resp.headers().get("content-type"));
        String sBuff = buff.toString();
        String[] elems = sBuff.split("\n");
        assertEquals(expected.size(), elems.length);
        for (String elem: elems) {
          assertTrue(expected.contains(elem));
        }
      });
    }, 200, "OK", null);
  }

  @Test
  public void testDirectoryListingJson() throws Exception {
    stat.setDirectoryListing(true);
    Set<String> expected = new HashSet<>(Arrays.asList(".hidden.html", "foo.json", "index.html", "otherpage.html", "a", "somedir", "somedir2", "somedir3", "testCompressionSuffix.html", "file with spaces.html"));
    testRequest(HttpMethod.GET, "/", req -> {
      req.putHeader("accept", "application/json");
    }, resp -> {
      resp.bodyHandler(buff -> {
        assertEquals("application/json", resp.headers().get("content-type"));
        String sBuff = buff.toString();
        JsonArray arr = new JsonArray(sBuff);
        assertEquals(expected.size(), arr.size());
        for (Object elem: arr) {
          assertTrue(expected.contains(elem));
        }
        testComplete();
      });
    }, 200, "OK", null);
    await();
  }

  @Test
  public void testDirectoryListingJsonNoHidden() throws Exception {
    stat.setDirectoryListing(true);
    stat.setIncludeHidden(false);
    Set<String> expected = new HashSet<>(Arrays.asList("foo.json", "a", "index.html", "otherpage.html", "somedir", "somedir2", "somedir3", "testCompressionSuffix.html", "file with spaces.html"));
    testRequest(HttpMethod.GET, "/", req -> {
      req.putHeader("accept", "application/json");
    }, resp -> {
      resp.bodyHandler(buff -> {
        assertEquals("application/json", resp.headers().get("content-type"));
        String sBuff = buff.toString();
        JsonArray arr = new JsonArray(sBuff);
        assertEquals(expected.size(), arr.size());
        for (Object elem: arr) {
          assertTrue(expected.contains(elem));
        }
        testComplete();
      });
    }, 200, "OK", null);
    await();
  }

  @Test
  public void testDirectoryListingHtml() throws Exception {
    stat.setDirectoryListing(true);

    testDirectoryListingHtmlCustomTemplate("vertx-web-directory.html");
  }

  @Test
  public void testCustomDirectoryListingHtml() throws Exception {
    stat.setDirectoryListing(true);
    String dirTemplate = "custom_dir_template.html";
    stat.setDirectoryTemplate(dirTemplate);

    testDirectoryListingHtmlCustomTemplate(dirTemplate);
  }

  private void testDirectoryListingHtmlCustomTemplate(String dirTemplateFile) throws Exception {
    stat.setDirectoryListing(true);


    String directoryTemplate = Utils.readResourceToBuffer(dirTemplateFile).toString();

    String parentLink = "<a href=\"/\">..</a>";
    String files = "<ul id=\"files\"><li><a href=\"/somedir2/foo2.json\" title=\"foo2.json\">foo2.json</a></li>" +
      "<li><a href=\"/somedir2/somepage.html\" title=\"somepage.html\">somepage.html</a></li>" +
      "<li><a href=\"/somedir2/somepage2.html\" title=\"somepage2.html\">somepage2.html</a></li></ul>";

    String expected = directoryTemplate.replace("{directory}", "/somedir2/").replace("{parent}", parentLink).replace("{files}", files);

    testRequest(HttpMethod.GET, "/somedir2", req -> req.putHeader("accept", "text/html"), resp -> resp.bodyHandler(buff -> {
      assertEquals("text/html", resp.headers().get("content-type"));
      String sBuff = buff.toString();
      assertEquals(expected, sBuff);
      testComplete();
    }), 200, "OK", null);
    await();
  }

  @Test
  public void testFSBlockingTuning() throws Exception {
    stat.setCachingEnabled(false);
    stat.setMaxAvgServeTimeNs(10000);
    for (int i = 0; i < 2000; i++) {
      testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
        String cacheControl = res.headers().get("cache-control");
        String lastModified = res.headers().get("last-modified");
        assertNull(cacheControl);
        assertNull(lastModified);
      }, 200, "OK", "<html><body>Other page</body></html>");
    }
  }

  @Test
  public void testServerRelativeToPath() throws Exception {
    router.clear();
    router.route("/somedir/*").handler(stat);
    testRequest(HttpMethod.GET, "/somedir/otherpage.html", 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testServerRelativeToPathAndMountPoint() throws Exception {
    router.clear();
    Router subRouter = Router.router(vertx);
    subRouter.route("/somedir/*").handler(stat);
    router.mountSubRouter("/mymount/", subRouter);
    testRequest(HttpMethod.GET, "/mymount/somedir/otherpage.html", 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testRangeAwareRequestHeaders() throws Exception {
    stat.setEnableRangeSupport(true);
    // this is a 3 step test
    // 1. request a head to a static image, this should tell us the server supports ranges
    // 2. make a request of the 1st 1000 bytes
    // 3. request all bytes after 1000
    // 4. request bytes from 1000 up to 5000000 if available (which isn't)

    testRequest(HttpMethod.HEAD, "/somedir/range.jpg", null, res -> {
      assertEquals("bytes", res.headers().get("Accept-Ranges"));
      assertEquals("15783", res.headers().get("Content-Length"));
    }, 200, "OK", null);

    testRequest(HttpMethod.GET, "/somedir/range.jpg", req -> req.headers().set("Range", "bytes=0-999"), res -> {
      assertEquals("bytes", res.headers().get("Accept-Ranges"));
      assertEquals("1000", res.headers().get("Content-Length"));
      assertEquals("bytes 0-999/15783", res.headers().get("Content-Range"));
    }, 206, "Partial Content", null);

    testRequest(HttpMethod.GET, "/somedir/range.jpg", req -> req.headers().set("Range", "bytes=1000-"), res -> {
      assertEquals("bytes", res.headers().get("Accept-Ranges"));
      assertEquals("14783", res.headers().get("Content-Length"));
      assertEquals("bytes 1000-15782/15783", res.headers().get("Content-Range"));
    }, 206, "Partial Content", null);
    testRequest(HttpMethod.GET, "/somedir/range.jpg", req -> req.headers().set("Range", "bytes=1000-5000000"), res -> {
      assertEquals("bytes", res.headers().get("Accept-Ranges"));
      assertEquals("14783", res.headers().get("Content-Length"));
      assertEquals("bytes 1000-15782/15783", res.headers().get("Content-Range"));
    }, 206, "Partial Content", null);
  }

  @Test
  public void testRangeAwareRequestBody() throws Exception {
    stat.setEnableRangeSupport(true);
    testRequest(HttpMethod.GET, "/somedir/range.jpg", req -> req.headers().set("Range", "bytes=0-999"), res -> res.bodyHandler(buff -> {
      assertEquals("bytes", res.headers().get("Accept-Ranges"));
      assertEquals("1000", res.headers().get("Content-Length"));
      assertEquals("bytes 0-999/15783", res.headers().get("Content-Range"));

      assertEquals(1000, buff.length());
      testComplete();
    }), 206, "Partial Content", null);
    await();
  }

  @Test
  public void testRangeAwareRequestBodyForDisabledRangeSupport() throws Exception {
    stat.setEnableRangeSupport(false);
    testRequest(HttpMethod.GET, "/somedir/range.jpg", req -> req.headers().set("Range", "bytes=0-999"), res -> res.bodyHandler(buff -> {
      assertNull(res.headers().get("Accept-Ranges"));
      assertNotSame("1000", res.headers().get("Content-Length"));

      assertNotSame(1000, buff.length());
      testComplete();
    }), 200, "OK", null);
    await();
  }

  @Test
  public void testOutOfRangeRequestBody() throws Exception {
    stat.setEnableRangeSupport(true);
    testRequest(HttpMethod.GET, "/somedir/range.jpg", req -> req.headers().set("Range", "bytes=15783-"), res -> res.bodyHandler(buff -> {
      assertEquals("bytes */15783", res.headers().get("Content-Range"));
      testComplete();
    }), 416, "Requested Range Not Satisfiable", null);
    await();
  }

  @Test
  public void testContentTypeSupport() throws Exception {
    testRequest(HttpMethod.GET, "/somedir/range.jpg", req -> {
    }, res -> {
      assertNotNull(res.getHeader("Content-Type"));
      assertEquals("image/jpeg", res.getHeader("Content-Type"));
      testComplete();
    }, 200, "OK", null);
    await();
  }

  @Test
  public void testAsyncExceptionIssue231() throws Exception {
    stat.setAlwaysAsyncFS(true);
    testRequest(HttpMethod.GET, "/non_existing.html", 404, "Not Found");
  }

  @Test
  public void testServerFileSystemPath() throws Exception {
    router.clear();

    File file = File.createTempFile("vertx", "tmp");
    file.deleteOnExit();

    // remap stat to the temp dir
    try {
      stat = StaticHandler.create(file.getParent());
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }

    stat = StaticHandler.create().setAllowRootFileSystemAccess(true).setWebRoot(file.getParent());
    router.route().handler(stat);

    testRequest(HttpMethod.GET, "/" + file.getName(), 200, "OK", "");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAccessToRootPath() throws Exception {
    router.clear();

    File file = File.createTempFile("vertx", "tmp");
    file.deleteOnExit();

    // remap stat to the temp dir
    stat = StaticHandler.create().setWebRoot(file.getParent());
  }

  @Test
  public void testLastModifiedInGMT() throws Exception {
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String lastModified = res.headers().get("last-modified");
      assertTrue(lastModified.endsWith("GMT"));
    }, 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testChangeDefaultContentEncoding() throws Exception {
    stat.setDefaultContentEncoding("ISO-8859-1");
    testRequest(HttpMethod.GET, "/otherpage.html", null, res -> {
      String contentType = res.headers().get("Content-Type");
      System.out.println(contentType);
      assertEquals("text/html;charset=ISO-8859-1", contentType);
    }, 200, "OK", "<html><body>Other page</body></html>");
  }

  @Test
  public void testHandlerAfter() throws Exception {
    router.get().handler(ctx -> ctx.response().end("Howdy!"));
    testRequest(HttpMethod.GET, "/not-existing-file.html", 200, "OK", "Howdy!");
  }


  // TODO
  // 1.Test all the params including invalid values
  // 2. Make sure exists isn't being called too many times

  private long toDateTime(String header) {
    try {
      Date date = dateTimeFormatter.parse(header);
      return date.getTime();
    } catch (Exception e) {
      fail(e.getMessage());
      return -1;
    }
  }

  private long fileSize(String filename) {
    return new File(filename).length();
  }
}

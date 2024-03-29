import org.apache.catalina.Globals;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.servlets.Constants;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.util.URLEncoder;
import org.apache.naming.resources.CacheEntry;
import org.apache.naming.resources.ProxyDirContext;
import org.apache.naming.resources.Resource;
import org.apache.naming.resources.ResourceAttributes;

import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;



public class RootServlet2 extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables


    /**
     * The debugging detail level for this servlet.
     */
    protected int debug = 0;


    /**
     * The input buffer size to use when serving resources.
     */
    protected int input = 2048;


    /**
     * Should we generate directory listings?
     */
    protected boolean listings = false;


    /**
     * Read only flag. By default, it's set to true.
     */
    protected boolean readOnly = true;


    /**
     * The output buffer size to use when serving resources.
     */
    protected int output = 2048;


    /**
     * Array containing the safe characters set.
     */
    protected static URLEncoder urlEncoder;


    /**
     * Allow customized directory listing per directory.
     */
    protected String localXsltFile = null;


    /**
     * Allow customized directory listing per context.
     */
    protected String contextXsltFile = null;


    /**
     * Allow customized directory listing per instance.
     */
    protected String globalXsltFile = null;


    /**
     * Allow a readme file to be included.
     */
    protected String readmeFile = null;


    /**
     * Proxy directory context.
     */
    protected ProxyDirContext resources = null;


    /**
     * File encoding to be used when reading static files. If none is specified
     * the platform default is used.
     */
    protected String fileEncoding = null;


    /**
     * Minimum size for sendfile usage in bytes.
     */
    protected int sendfileSize = 48 * 1024;

    /**
     * Should the Accept-Ranges: bytes header be send with static resources?
     */
    protected boolean useAcceptRanges = true;

    /**
     * Full range marker.
     */
    protected static ArrayList<Range> FULL = new ArrayList<>();


    // ----------------------------------------------------- Static Initializer


    /**
     * GMT timezone - all HTTP dates are on GMT
     */
    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');
    }


    /**
     * MIME multipart separation string
     */
    protected static final String mimeSeparation = "CATALINA_MIME_BOUNDARY";


    /**
     * JNDI resources name.
     */
    protected static final String RESOURCES_JNDI_NAME = "java:/comp/Resources";


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
            StringManager.getManager(Constants.Package);


    /**
     * Size of file transfer buffer in bytes.
     */
    protected static final int BUFFER_SIZE = 4096;


    // --------------------------------------------------------- Public Methods


    /**
     * Finalize this servlet.
     */
    @Override
    public void destroy() {
        // NOOP
    }


    /**
     * Initialize this servlet.
     */
    @Override
    public void init() throws ServletException {

        if (getServletConfig().getInitParameter("debug") != null)
            debug = Integer.parseInt(getServletConfig().getInitParameter("debug"));

        if (getServletConfig().getInitParameter("input") != null)
            input = Integer.parseInt(getServletConfig().getInitParameter("input"));

        if (getServletConfig().getInitParameter("output") != null)
            output = Integer.parseInt(getServletConfig().getInitParameter("output"));

        listings = Boolean.parseBoolean(getServletConfig().getInitParameter("listings"));

        if (getServletConfig().getInitParameter("readonly") != null)
            readOnly = Boolean.parseBoolean(getServletConfig().getInitParameter("readonly"));

        if (getServletConfig().getInitParameter("sendfileSize") != null)
            sendfileSize =
                    Integer.parseInt(getServletConfig().getInitParameter("sendfileSize")) * 1024;

        fileEncoding = getServletConfig().getInitParameter("fileEncoding");

        globalXsltFile = getServletConfig().getInitParameter("globalXsltFile");
        contextXsltFile = getServletConfig().getInitParameter("contextXsltFile");
        localXsltFile = getServletConfig().getInitParameter("localXsltFile");
        readmeFile = getServletConfig().getInitParameter("readmeFile");

        if (getServletConfig().getInitParameter("useAcceptRanges") != null)
            useAcceptRanges = Boolean.parseBoolean(getServletConfig().getInitParameter("useAcceptRanges"));

        // Sanity check on the specified buffer sizes
        if (input < 256)
            input = 256;
        if (output < 256)
            output = 256;

        if (debug > 0) {
            log("DefaultServlet.init:  input buffer size=" + input +
                    ", output buffer size=" + output);
        }

        // Load the proxy dir context.
        resources = (ProxyDirContext) getServletContext()
                .getAttribute(Globals.RESOURCES_ATTR);
        if (resources == null) {
            try {
                resources =
                        (ProxyDirContext) new InitialContext()
                                .lookup(RESOURCES_JNDI_NAME);
            } catch (NamingException e) {
                // Failed
                throw new ServletException("No resources", e);
            }
        }

        if (resources == null) {
            throw new UnavailableException("No resources");
        }

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Return the relative path associated with this servlet.
     *
     * @param request The servlet request we are processing
     */
    protected String getRelativePath(HttpServletRequest request) {

        // Are we being processed by a RequestDispatcher.include()?
        if (request.getAttribute(Globals.INCLUDE_REQUEST_URI_ATTR) != null) {
            String result = (String) request.getAttribute(
                    Globals.INCLUDE_PATH_INFO_ATTR);
            if (result == null)
                result = (String) request.getAttribute(
                        Globals.INCLUDE_SERVLET_PATH_ATTR);
            if ((result == null) || (result.equals("")))
                result = "/";
            return (result);
        }

        // No, extract the desired path directly from the request
        String result = request.getPathInfo();
        if (result == null) {
            result = request.getServletPath();
        }
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return (result);

    }


    /**
     * Process a GET request for the specified resource.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @throws IOException      if an input/output error occurs
     * @throws ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException, ServletException {

        // Serve the requested resource, including the data content
        serveResource(request, response, true);

    }


    /**
     * Process a HEAD request for the specified resource.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @throws IOException      if an input/output error occurs
     * @throws ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doHead(HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException, ServletException {

        // Serve the requested resource, without the data content
        serveResource(request, response, false);

    }


    /**
     * Override default implementation to ensure that TRACE is correctly
     * handled.
     *
     * @param req  the {@link HttpServletRequest} object that
     *             contains the request the client made of
     *             the servlet
     * @param resp the {@link HttpServletResponse} object that
     *             contains the response the servlet returns
     *             to the client
     * @throws IOException      if an input or output error occurs
     *                          while the servlet is handling the
     *                          OPTIONS request
     * @throws ServletException if the request for the
     *                          OPTIONS cannot be handled
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        StringBuilder allow = new StringBuilder();
        // There is a doGet method
        allow.append("GET, HEAD");
        // There is a doPost
        allow.append(", POST");
        // There is a doPut
        allow.append(", PUT");
        // There is a doDelete
        allow.append(", DELETE");
        // Trace - assume disabled unless we can prove otherwise
        if (req instanceof RequestFacade &&
                ((RequestFacade) req).getAllowTrace()) {
            allow.append(", TRACE");
        }
        // Always allow options
        allow.append(", OPTIONS");

        resp.setHeader("Allow", allow.toString());
    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @throws IOException      if an input/output error occurs
     * @throws ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException, ServletException {
        doGet(request, response);
    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param req  The servlet request we are processing
     * @param resp The servlet response we are creating
     * @throws IOException      if an input/output error occurs
     * @throws ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        boolean exists = true;
        try {
            resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        boolean result = true;

        // Temp. content file used to support partial PUT
        File contentFile;

        Range range = parseContentRange(req, resp);

        InputStream resourceInputStream;

        // Append data specified in ranges to existing content for this
        // resource - create a temp. file on the local filesystem to
        // perform this operation
        // Assume just one range is specified for now
        if (range != null) {
            contentFile = executePartialPut(req, range, path);
            resourceInputStream = new FileInputStream(contentFile);
        } else {
            resourceInputStream = req.getInputStream();
        }

        try {
            Resource newResource = new Resource(resourceInputStream);
            // FIXME: Add attributes
            if (exists) {
                resources.rebind(path, newResource);
            } else {
                resources.bind(path, newResource);
            }
        } catch (NamingException e) {
            result = false;
        }

        if (result) {
            if (exists) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.setStatus(HttpServletResponse.SC_CREATED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_CONFLICT);
        }

    }


    /**
     * Handle a partial PUT.  New content specified in request is appended to
     * existing content in oldRevisionContent (if present). This code does
     * not support simultaneous partial updates to the same resource.
     */
    protected File executePartialPut(HttpServletRequest req, Range range,
                                     String path)
            throws IOException {

        // Append data specified in ranges to existing content for this
        // resource - create a temp. file on the local filesystem to
        // perform this operation
        File tempDir = (File) getServletContext().getAttribute
                ("javax.servlet.context.tempdir");
        // Convert all '/' characters to '.' in resourcePath
        String convertedResourcePath = path.replace('/', '.');
        File contentFile = new File(tempDir, convertedResourcePath);
        if (contentFile.createNewFile()) {
            // Clean up contentFile when Tomcat is terminated
            contentFile.deleteOnExit();
        }

        RandomAccessFile randAccessContentFile =
                new RandomAccessFile(contentFile, "rw");

        Resource oldResource = null;
        try {
            Object obj = resources.lookup(path);
            if (obj instanceof Resource)
                oldResource = (Resource) obj;
        } catch (NamingException e) {
            // Ignore
        }

        // Copy data in oldRevisionContent to contentFile
        if (oldResource != null) {
            BufferedInputStream bufOldRevStream =
                    new BufferedInputStream(oldResource.streamContent(),
                            BUFFER_SIZE);

            int numBytesRead;
            byte[] copyBuffer = new byte[BUFFER_SIZE];
            while ((numBytesRead = bufOldRevStream.read(copyBuffer)) != -1) {
                randAccessContentFile.write(copyBuffer, 0, numBytesRead);
            }

            bufOldRevStream.close();
        }

        randAccessContentFile.setLength(range.length);

        // Append data in request input stream to contentFile
        randAccessContentFile.seek(range.start);
        int numBytesRead;
        byte[] transferBuffer = new byte[BUFFER_SIZE];
        BufferedInputStream requestBufInStream =
                new BufferedInputStream(req.getInputStream(), BUFFER_SIZE);
        while ((numBytesRead = requestBufInStream.read(transferBuffer)) != -1) {
            randAccessContentFile.write(transferBuffer, 0, numBytesRead);
        }
        randAccessContentFile.close();
        requestBufInStream.close();

        return contentFile;

    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param req  The servlet request we are processing
     * @param resp The servlet response we are creating
     * @throws IOException      if an input/output error occurs
     * @throws ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        boolean exists = true;
        try {
            resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        if (exists) {
            boolean result = true;
            try {
                resources.unbind(path);
            } catch (NamingException e) {
                result = false;
            }
            if (result) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }

    }


    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param request            The servlet request we are processing
     * @param response           The servlet response we are creating
     * @param resourceAttributes The resource information
     * @return boolean true if the resource meets all the specified conditions,
     * and false if any of the conditions is not satisfied, in which case
     * request processing is stopped
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ResourceAttributes resourceAttributes)
            throws IOException {

        return checkIfMatch(request, response, resourceAttributes)
                && checkIfModifiedSince(request, response, resourceAttributes)
                && checkIfNoneMatch(request, response, resourceAttributes)
                && checkIfUnmodifiedSince(request, response, resourceAttributes);

    }


    /**
     * URL rewriter.
     *
     * @param path Path which has to be rewritten
     */
    protected String rewriteUrl(String path) {
        return urlEncoder.encode(path);
    }


    /**
     * Display the size of a file.
     */
    protected void displaySize(StringBuilder buf, int filesize) {

        int leftside = filesize / 1024;
        int rightside = (filesize % 1024) / 103;  // makes 1 digit
        // To avoid 0.0 for non-zero file, we bump to 0.1
        if (leftside == 0 && rightside == 0 && filesize != 0)
            rightside = 1;
        buf.append(leftside).append(".").append(rightside);
        buf.append(" KB");

    }


    /**
     * Serve the specified resource, optionally including the data content.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param content  Should the content be included?
     * @throws IOException      if an input/output error occurs
     * @throws ServletException if a servlet-specified error occurs
     */
    protected void serveResource(HttpServletRequest request,
                                 HttpServletResponse response,
                                 boolean content)
            throws IOException, ServletException {

        boolean serveContent = content;

        // Identify the requested resource path
        String path = getRelativePath(request);
        if (debug > 0) {
            if (serveContent)
                log("DefaultServlet.serveResource:  Serving resource '" +
                        path + "' headers and data");
            else
                log("DefaultServlet.serveResource:  Serving resource '" +
                        path + "' headers only");
        }

        CacheEntry cacheEntry = resources.lookupCache(path);

        if (!cacheEntry.exists) {
            // Check if we're included so we can return the appropriate
            // missing resource name in the error
            String requestUri = (String) request.getAttribute(
                    Globals.INCLUDE_REQUEST_URI_ATTR);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            } else {
                // We're included
                // SRV.9.3 says we must throw a FNFE
                throw new FileNotFoundException(
                        sm.getString("defaultServlet.missingResource",
                                requestUri));
            }

            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    requestUri);
            return;
        }

        // If the resource is not a collection, and the resource path
        // ends with "/" or "\", return NOT FOUND
        if (cacheEntry.context == null) {
            if (path.endsWith("/") || (path.endsWith("\\"))) {
                // Check if we're included so we can return the appropriate
                // missing resource name in the error
                String requestUri = (String) request.getAttribute(
                        Globals.INCLUDE_REQUEST_URI_ATTR);
                if (requestUri == null) {
                    requestUri = request.getRequestURI();
                }
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        requestUri);
                return;
            }
        }

        // Check if the conditions specified in the optional If headers are
        // satisfied.
        if (cacheEntry.context == null) {

            // Checking If headers
            boolean included =
                    (request.getAttribute(Globals.INCLUDE_CONTEXT_PATH_ATTR) != null);
            if (!included
                    && !checkIfHeaders(request, response, cacheEntry.attributes)) {
                return;
            }

        }

        // Find content type.
        String contentType = cacheEntry.attributes.getMimeType();
        if (contentType == null) {
            contentType = getServletContext().getMimeType(cacheEntry.name);
            cacheEntry.attributes.setMimeType(contentType);
        }

        ArrayList<Range> ranges = null;
        long contentLength = -1L;

        if (cacheEntry.context != null) {

            // Skip directory listings if we have been configured to
            // suppress them
            if (!listings) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        request.getRequestURI());
                return;
            }
            contentType = "text/html;charset=UTF-8";

        } else {
            if (useAcceptRanges) {
                // Accept ranges header
                response.setHeader("Accept-Ranges", "bytes");
            }

            // Parse range specifier
            ranges = parseRange(request, response, cacheEntry.attributes);

            // ETag header
            response.setHeader("ETag", cacheEntry.attributes.getETag());

            // Last-Modified header
            response.setHeader("Last-Modified",
                    cacheEntry.attributes.getLastModifiedHttp());

            // Get content length
            contentLength = cacheEntry.attributes.getContentLength();
            // Special case for zero length files, which would cause a
            // (silent) ISE when setting the output buffer size
            if (contentLength == 0L) {
                serveContent = false;
            }

        }

        ServletOutputStream ostream = null;
        PrintWriter writer = null;

        if (serveContent) {

            // Trying to retrieve the servlet output stream

            try {
                ostream = response.getOutputStream();
            } catch (IllegalStateException e) {
                // If it fails, we try to get a Writer instead if we're
                // trying to serve a text file
                if ((contentType == null)
                        || (contentType.startsWith("text"))
                        || (contentType.endsWith("xml"))) {
                    writer = response.getWriter();
                } else {
                    throw e;
                }
            }

        }

        if ((cacheEntry.context != null)
                || (((ranges == null) || (ranges.isEmpty()))
                && (request.getHeader("Range") == null))
                || (ranges == FULL)) {

            // Set the appropriate output headers
            if (contentType != null) {
                if (debug > 0)
                    log("DefaultServlet.serveFile:  contentType='" +
                            contentType + "'");
                response.setContentType(contentType);
            }
            if ((cacheEntry.resource != null) && (contentLength >= 0)) {
                if (debug > 0)
                    log("DefaultServlet.serveFile:  contentLength=" +
                            contentLength);
                if (contentLength < Integer.MAX_VALUE) {
                    response.setContentLength((int) contentLength);
                } else {
                    // Set the content-length as String to be able to use a long
                    response.setHeader("content-length", "" + contentLength);
                }
            }

            InputStream renderResult = null;
            if (cacheEntry.context != null) {

                if (serveContent) {
                    // Serve the directory browser
                    renderResult =
                            render(request.getContextPath(), cacheEntry);
                }

            }

            // Copy the input stream to our output stream (if requested)
            if (serveContent) {
                try {
                    response.setBufferSize(output);
                } catch (IllegalStateException e) {
                    // Silent catch
                }
                if (ostream != null) {
                    if (!checkSendfile(request, response, cacheEntry, contentLength, null))
                        copy(cacheEntry, renderResult, ostream);
                } else {
                    copy(cacheEntry, renderResult, writer);
                }
            }

        } else {

            if ((ranges == null) || (ranges.isEmpty()))
                return;

            // Partial content response.

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

            if (ranges.size() == 1) {

                Range range = ranges.get(0);
                response.addHeader("Content-Range", "bytes "
                        + range.start
                        + "-" + range.end + "/"
                        + range.length);
                long length = range.end - range.start + 1;
                if (length < Integer.MAX_VALUE) {
                    response.setContentLength((int) length);
                } else {
                    // Set the content-length as String to be able to use a long
                    response.setHeader("content-length", "" + length);
                }

                if (contentType != null) {
                    if (debug > 0)
                        log("DefaultServlet.serveFile:  contentType='" +
                                contentType + "'");
                    response.setContentType(contentType);
                }

                if (serveContent) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        if (!checkSendfile(request, response, cacheEntry, range.end - range.start + 1, range))
                            copy(cacheEntry, ostream, range);
                    } else {
                        copy(cacheEntry, writer, range);
                    }
                }

            } else {

                response.setContentType("multipart/byteranges; boundary="
                        + mimeSeparation);

                if (serveContent) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        copy(cacheEntry, ostream, ranges.iterator(),
                                contentType);
                    } else {
                        copy(cacheEntry, writer, ranges.iterator(),
                                contentType);
                    }
                }

            }

        }

    }


    /**
     * Parse the content-range header.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @return Range
     */
    protected Range parseContentRange(HttpServletRequest request,
                                      HttpServletResponse response)
            throws IOException {

        // Retrieving the content-range header (if any is specified
        String rangeHeader = request.getHeader("Content-Range");

        if (rangeHeader == null)
            return null;

        // bytes is the only range unit supported
        if (!rangeHeader.startsWith("bytes")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        rangeHeader = rangeHeader.substring(6).trim();

        int dashPos = rangeHeader.indexOf('-');
        int slashPos = rangeHeader.indexOf('/');

        if (dashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (slashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        Range range = new Range();

        try {
            range.start = Long.parseLong(rangeHeader.substring(0, dashPos));
            range.end =
                    Long.parseLong(rangeHeader.substring(dashPos + 1, slashPos));
            range.length = Long.parseLong
                    (rangeHeader.substring(slashPos + 1, rangeHeader.length()));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (!range.validate()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        return range;

    }


    /**
     * Parse the range header.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @return Vector of ranges
     */
    protected ArrayList<Range> parseRange(HttpServletRequest request,
                                          HttpServletResponse response,
                                          ResourceAttributes resourceAttributes) throws IOException {

        // Checking If-Range
        String headerValue = request.getHeader("If-Range");

        if (headerValue != null) {

            long headerValueTime = (-1L);
            try {
                headerValueTime = request.getDateHeader("If-Range");
            } catch (IllegalArgumentException e) {
                // Ignore
            }

            String eTag = resourceAttributes.getETag();
            long lastModified = resourceAttributes.getLastModified();

            if (headerValueTime == (-1L)) {

                // If the ETag the client gave does not match the entity
                // etag, then the entire entity is returned.
                if (!eTag.equals(headerValue.trim()))
                    return FULL;

            } else {

                // If the timestamp of the entity the client got is older than
                // the last modification date of the entity, the entire entity
                // is returned.
                if (lastModified > (headerValueTime + 1000))
                    return FULL;

            }

        }

        long fileLength = resourceAttributes.getContentLength();

        if (fileLength == 0)
            return null;

        // Retrieving the range header (if any is specified
        String rangeHeader = request.getHeader("Range");

        if (rangeHeader == null)
            return null;
        // bytes is the only range unit supported (and I don't see the point
        // of adding new ones).
        if (!rangeHeader.startsWith("bytes")) {
            response.addHeader("Content-Range", "bytes */" + fileLength);
            response.sendError
                    (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return null;
        }

        rangeHeader = rangeHeader.substring(6);

        // Vector which will contain all the ranges which are successfully
        // parsed.
        ArrayList<Range> result = new ArrayList<>();
        StringTokenizer commaTokenizer = new StringTokenizer(rangeHeader, ",");

        // Parsing the range list
        while (commaTokenizer.hasMoreTokens()) {
            String rangeDefinition = commaTokenizer.nextToken().trim();

            Range currentRange = new Range();
            currentRange.length = fileLength;

            int dashPos = rangeDefinition.indexOf('-');

            if (dashPos == -1) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError
                        (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }

            if (dashPos == 0) {

                try {
                    long offset = Long.parseLong(rangeDefinition);
                    currentRange.start = fileLength + offset;
                    currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    response.addHeader("Content-Range",
                            "bytes */" + fileLength);
                    response.sendError
                            (HttpServletResponse
                                    .SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return null;
                }

            } else {

                try {
                    currentRange.start = Long.parseLong
                            (rangeDefinition.substring(0, dashPos));
                    if (dashPos < rangeDefinition.length() - 1)
                        currentRange.end = Long.parseLong
                                (rangeDefinition.substring
                                        (dashPos + 1, rangeDefinition.length()));
                    else
                        currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    response.addHeader("Content-Range",
                            "bytes */" + fileLength);
                    response.sendError
                            (HttpServletResponse
                                    .SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return null;
                }

            }

            if (!currentRange.validate()) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError
                        (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }

            result.add(currentRange);
        }

        return result;
    }


    /**
     * Decide which way to render. HTML or XML.
     */
    protected InputStream render(String contextPath, org.apache.naming.resources.CacheEntry cacheEntry)
            throws IOException, ServletException {

        InputStream xsltInputStream =
                findXsltInputStream(cacheEntry.context);

        if (xsltInputStream == null) {
            return renderHtml(contextPath, cacheEntry);
        }
        return renderXml(contextPath, cacheEntry, xsltInputStream);

    }

    /**
     * Return an InputStream to an HTML representation of the contents
     * of this directory.
     *
     * @param contextPath Context path to which our internal paths are
     *                    relative
     */
    protected InputStream renderXml(String contextPath,
                                    org.apache.naming.resources.CacheEntry cacheEntry,
                                    InputStream xsltInputStream)
            throws IOException, ServletException {

        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<listing ");
        sb.append(" contextPath='");
        sb.append(contextPath);
        sb.append("'");
        sb.append(" directory='");
        sb.append(cacheEntry.name);
        sb.append("' ");
        sb.append(" hasParent='").append(!cacheEntry.name.equals("/"));
        sb.append("'>");

        sb.append("<entries>");

        try {

            // Render the directory entries within this directory
            NamingEnumeration<NameClassPair> enumeration =
                    resources.list(cacheEntry.name);

            // rewriteUrl(contextPath) is expensive. cache result for later reuse
            String rewrittenContextPath = rewriteUrl(contextPath);

            while (enumeration.hasMoreElements()) {

                NameClassPair ncPair = enumeration.nextElement();
                String resourceName = ncPair.getName();
                String trimmed = resourceName/*.substring(trim)*/;
                if (trimmed.equalsIgnoreCase("WEB-INF") ||
                        trimmed.equalsIgnoreCase("META-INF") ||
                        trimmed.equalsIgnoreCase(localXsltFile))
                    continue;

                if ((cacheEntry.name + trimmed).equals(contextXsltFile))
                    continue;

                CacheEntry childCacheEntry =
                        resources.lookupCache(cacheEntry.name + resourceName);
                if (!childCacheEntry.exists) {
                    continue;
                }

                sb.append("<entry");
                sb.append(" type='")
                        .append((childCacheEntry.context != null) ? "dir" : "file")
                        .append("'");
                sb.append(" urlPath='")
                        .append(rewrittenContextPath)
                        .append(rewriteUrl(cacheEntry.name + resourceName))
                        .append((childCacheEntry.context != null) ? "/" : "")
                        .append("'");
                if (childCacheEntry.resource != null) {
                    sb.append(" size='")
                            .append(renderSize(childCacheEntry.attributes.getContentLength()))
                            .append("'");
                }
                sb.append(" date='")
                        .append(childCacheEntry.attributes.getLastModifiedHttp())
                        .append("'");

                sb.append(">");
                sb.append(RequestUtil.filter(trimmed));
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("</entry>");

            }

        } catch (NamingException e) {
            // Something went wrong
            throw new ServletException("Error accessing resource", e);
        }

        sb.append("</entries>");

        String readme = getReadme(cacheEntry.context);

        if (readme != null) {
            sb.append("<readme><![CDATA[");
            sb.append(readme);
            sb.append("]]></readme>");
        }


        sb.append("</listing>");


        try {
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Source xmlSource = new StreamSource(new StringReader(sb.toString()));
            Source xslSource = new StreamSource(xsltInputStream);
            Transformer transformer = tFactory.newTransformer(xslSource);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF8");
            StreamResult out = new StreamResult(osWriter);
            transformer.transform(xmlSource, out);
            osWriter.flush();
            return (new ByteArrayInputStream(stream.toByteArray()));
        } catch (TransformerException e) {
            throw new ServletException("XSL transformer error", e);
        }
    }

    /**
     * Return an InputStream to an HTML representation of the contents
     * of this directory.
     *
     * @param contextPath Context path to which our internal paths are
     *                    relative
     */
    protected InputStream renderHtml(String contextPath, org.apache.naming.resources.CacheEntry cacheEntry)
            throws IOException, ServletException {

        String name = cacheEntry.name;

        // Prepare a writer to a buffered area
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF8");
        PrintWriter writer = new PrintWriter(osWriter);

        StringBuilder sb = new StringBuilder();

        // rewriteUrl(contextPath) is expensive. cache result for later reuse
        String rewrittenContextPath = rewriteUrl(contextPath);

        // Render the page header
        sb.append("<html>\r\n");
        sb.append("<head>\r\n");
        sb.append("<title>");
        sb.append(sm.getString("directory.title", name));
        sb.append("</title>\r\n");
        sb.append("<STYLE><!--");
        sb.append(org.apache.catalina.util.TomcatCSS.TOMCAT_CSS);
        sb.append("--></STYLE> ");
        sb.append("</head>\r\n");
        sb.append("<body>");
        sb.append("<h1>");
        sb.append(sm.getString("directory.title", name));

        // Render the link to our parent (if required)
        String parentDirectory = name;
        if (parentDirectory.endsWith("/")) {
            parentDirectory =
                    parentDirectory.substring(0, parentDirectory.length() - 1);
        }
        int slash = parentDirectory.lastIndexOf('/');
        if (slash >= 0) {
            String parent = name.substring(0, slash);
            sb.append(" - <a href=\"");
            sb.append(rewrittenContextPath);
            if (parent.equals(""))
                parent = "/";
            sb.append(rewriteUrl(parent));
            if (!parent.endsWith("/"))
                sb.append("/");
            sb.append("\">");
            sb.append("<b>");
            sb.append(sm.getString("directory.parent", parent));
            sb.append("</b>");
            sb.append("</a>");
        }

        sb.append("</h1>");
        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        sb.append("<table width=\"100%\" cellspacing=\"0\"" +
                " cellpadding=\"5\" align=\"center\">\r\n");

        // Render the column headings
        sb.append("<tr>\r\n");
        sb.append("<td align=\"left\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.filename"));
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"center\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.size"));
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"right\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.lastModified"));
        sb.append("</strong></font></td>\r\n");
        sb.append("</tr>");

        try {

            // Render the directory entries within this directory
            NamingEnumeration<NameClassPair> enumeration =
                    resources.list(cacheEntry.name);
            boolean shade = false;
            while (enumeration.hasMoreElements()) {

                NameClassPair ncPair = enumeration.nextElement();
                String resourceName = ncPair.getName();
                String trimmed = resourceName/*.substring(trim)*/;
                if (trimmed.equalsIgnoreCase("WEB-INF") ||
                        trimmed.equalsIgnoreCase("META-INF"))
                    continue;

                CacheEntry childCacheEntry =
                        resources.lookupCache(cacheEntry.name + resourceName);
                if (!childCacheEntry.exists) {
                    continue;
                }

                sb.append("<tr");
                if (shade)
                    sb.append(" bgcolor=\"#eeeeee\"");
                sb.append(">\r\n");
                shade = !shade;

                sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
                sb.append("<a href=\"");
                sb.append(rewrittenContextPath);
                resourceName = rewriteUrl(name + resourceName);
                sb.append(resourceName);
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("\"><tt>");
                sb.append(RequestUtil.filter(trimmed));
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("</tt></a></td>\r\n");

                sb.append("<td align=\"right\"><tt>");
                if (childCacheEntry.context != null)
                    sb.append("&nbsp;");
                else
                    sb.append(renderSize(childCacheEntry.attributes.getContentLength()));
                sb.append("</tt></td>\r\n");

                sb.append("<td align=\"right\"><tt>");
                sb.append(childCacheEntry.attributes.getLastModifiedHttp());
                sb.append("</tt></td>\r\n");

                sb.append("</tr>\r\n");
            }

        } catch (NamingException e) {
            // Something went wrong
            throw new ServletException("Error accessing resource", e);
        }

        // Render the page footer
        sb.append("</table>\r\n");

        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        String readme = getReadme(cacheEntry.context);
        if (readme != null) {
            sb.append(readme);
            sb.append("<HR size=\"1\" noshade=\"noshade\">");
        }

        sb.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        sb.append("</body>\r\n");
        sb.append("</html>\r\n");

        // Return an input stream to the underlying bytes
        writer.write(sb.toString());
        writer.flush();
        return (new ByteArrayInputStream(stream.toByteArray()));

    }


    /**
     * Render the specified file size (in bytes).
     *
     * @param size File size (in bytes)
     */
    protected String renderSize(long size) {

        long leftSide = size / 1024;
        long rightSide = (size % 1024) / 103;   // Makes 1 digit
        if ((leftSide == 0) && (rightSide == 0) && (size > 0))
            rightSide = 1;

        return ("" + leftSide + "." + rightSide + " kb");

    }


    /**
     * Get the readme file as a string.
     */
    protected String getReadme(DirContext directory)
            throws IOException {

        if (readmeFile != null) {
            try {
                Object obj = directory.lookup(readmeFile);
                if ((obj != null) && (obj instanceof Resource)) {
                    StringWriter buffer = new StringWriter();
                    InputStream is = ((Resource) obj).streamContent();
                    copyRange(new InputStreamReader(is),
                            new PrintWriter(buffer));
                    return buffer.toString();
                }
            } catch (NamingException e) {
                if (debug > 10)
                    log("readme '" + readmeFile + "' not found", e);

                return null;
            }
        }

        return null;
    }


    /**
     * Return the xsl template inputstream (if possible)
     */
    protected InputStream findXsltInputStream(DirContext directory)
            throws IOException {

        if (localXsltFile != null) {
            try {
                Object obj = directory.lookup(localXsltFile);
                if ((obj != null) && (obj instanceof Resource)) {
                    InputStream is = ((Resource) obj).streamContent();
                    if (is != null)
                        return is;
                }
            } catch (NamingException e) {
                if (debug > 10)
                    log("localXsltFile '" + localXsltFile + "' not found", e);
            }
        }

        if (contextXsltFile != null) {
            InputStream is =
                    getServletContext().getResourceAsStream(contextXsltFile);
            if (is != null)
                return is;

            if (debug > 10)
                log("contextXsltFile '" + contextXsltFile + "' not found");
        }

        /*  Open and read in file in one fell swoop to reduce chance
         *  chance of leaving handle open.
         */
        if (globalXsltFile != null) {
            FileInputStream fis = null;

            try {
                File f = new File(globalXsltFile);
                if (f.exists()) {
                    fis = new FileInputStream(f);
                    byte b[] = new byte[(int) f.length()]; /* danger! */
                    fis.read(b);
                    return new ByteArrayInputStream(b);
                }
            } finally {
                if (fis != null)
                    fis.close();
            }
        }

        return null;

    }


    // -------------------------------------------------------- protected Methods


    /**
     * Check if sendfile can be used.
     */
    protected boolean checkSendfile(HttpServletRequest request,
                                    HttpServletResponse response,
                                    org.apache.naming.resources.CacheEntry entry,
                                    long length, Range range) {
        if ((sendfileSize > 0)
                && (entry.resource != null)
                && ((length > sendfileSize) || (entry.resource.getContent() == null))
                && (entry.attributes.getCanonicalPath() != null)
                && (Boolean.TRUE == request.getAttribute("org.apache.tomcat.sendfile.support"))
                && (request.getClass().getName().equals("org.apache.catalina.connector.RequestFacade"))
                && (response.getClass().getName().equals("org.apache.catalina.connector.ResponseFacade"))) {
            request.setAttribute("org.apache.tomcat.sendfile.filename", entry.attributes.getCanonicalPath());
            if (range == null) {
                request.setAttribute("org.apache.tomcat.sendfile.start", 0L);
                request.setAttribute("org.apache.tomcat.sendfile.end", length);
            } else {
                request.setAttribute("org.apache.tomcat.sendfile.start", range.start);
                request.setAttribute("org.apache.tomcat.sendfile.end", range.end + 1);
            }
            request.setAttribute("org.apache.tomcat.sendfile.token", this);
            return true;
        }
        return false;
    }



    protected boolean checkIfMatch(HttpServletRequest request,
                                   HttpServletResponse response,
                                   ResourceAttributes resourceAttributes)
            throws IOException {

        String eTag = resourceAttributes.getETag();
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {

                StringTokenizer commaTokenizer = new StringTokenizer
                        (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precodition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.sendError
                            (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }

            }
        }
        return true;

    }



    protected boolean checkIfModifiedSince(HttpServletRequest request,
                                           HttpServletResponse response,
                                           ResourceAttributes resourceAttributes) {
        try {
            long headerValue = request.getDateHeader("If-Modified-Since");
            long lastModified = resourceAttributes.getLastModified();
            if (headerValue != -1) {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null)
                        && (lastModified < headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", resourceAttributes.getETag());

                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }


    protected boolean checkIfNoneMatch(HttpServletRequest request,
                                       HttpServletResponse response,
                                       ResourceAttributes resourceAttributes)
            throws IOException {

        String eTag = resourceAttributes.getETag();
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                        new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if (("GET".equals(request.getMethod()))
                        || ("HEAD".equals(request.getMethod()))) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", eTag);

                    return false;
                }
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }
        return true;

    }


    protected boolean checkIfUnmodifiedSince(HttpServletRequest request,
                                             HttpServletResponse response,
                                             ResourceAttributes resourceAttributes)
            throws IOException {
        try {
            long lastModified = resourceAttributes.getLastModified();
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1) {
                if (lastModified >= (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }


    protected void copy(org.apache.naming.resources.CacheEntry cacheEntry, InputStream is,
                        ServletOutputStream ostream)
            throws IOException {

        IOException exception;
        InputStream resourceInputStream;

        // Optimization: If the binary content has already been loaded, send
        // it directly
        if (cacheEntry.resource != null) {
            byte buffer[] = cacheEntry.resource.getContent();
            if (buffer != null) {
                ostream.write(buffer, 0, buffer.length);
                return;
            }
            resourceInputStream = cacheEntry.resource.streamContent();
        } else {
            resourceInputStream = is;
        }

        InputStream istream = new BufferedInputStream
                (resourceInputStream, input);

        // Copy the input stream to the output stream
        exception = copyRange(istream, ostream);

        // Clean up the input stream
        istream.close();

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    protected void copy(org.apache.naming.resources.CacheEntry cacheEntry, InputStream is, PrintWriter writer)
            throws IOException {

        IOException exception;

        InputStream resourceInputStream;
        if (cacheEntry.resource != null) {
            resourceInputStream = cacheEntry.resource.streamContent();
        } else {
            resourceInputStream = is;
        }

        Reader reader;
        if (fileEncoding == null) {
            reader = new InputStreamReader(resourceInputStream);
        } else {
            reader = new InputStreamReader(resourceInputStream,
                    fileEncoding);
        }

        // Copy the input stream to the output stream
        exception = copyRange(reader, writer);

        // Clean up the reader
        reader.close();

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    protected void copy(org.apache.naming.resources.CacheEntry cacheEntry, ServletOutputStream ostream,
                        Range range)
            throws IOException {

        IOException exception;

        InputStream resourceInputStream = cacheEntry.resource.streamContent();
        InputStream istream =
                new BufferedInputStream(resourceInputStream, input);
        exception = copyRange(istream, ostream, range.start, range.end);

        // Clean up the input stream
        istream.close();

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    protected void copy(org.apache.naming.resources.CacheEntry cacheEntry, PrintWriter writer,
                        Range range)
            throws IOException {

        IOException exception;

        InputStream resourceInputStream = cacheEntry.resource.streamContent();

        Reader reader;
        if (fileEncoding == null) {
            reader = new InputStreamReader(resourceInputStream);
        } else {
            reader = new InputStreamReader(resourceInputStream,
                    fileEncoding);
        }

        exception = copyRange(reader, writer, range.start, range.end);

        // Clean up the input stream
        reader.close();

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    protected void copy(org.apache.naming.resources.CacheEntry cacheEntry, ServletOutputStream ostream,
                        Iterator<Range> ranges, String contentType)
            throws IOException {

        IOException exception = null;

        while ((exception == null) && (ranges.hasNext())) {

            InputStream resourceInputStream = cacheEntry.resource.streamContent();
            InputStream istream =
                    new BufferedInputStream(resourceInputStream, input);

            Range currentRange = ranges.next();

            // Writing MIME header.
            ostream.println();
            ostream.println("--" + mimeSeparation);
            if (contentType != null)
                ostream.println("Content-Type: " + contentType);
            ostream.println("Content-Range: bytes " + currentRange.start
                    + "-" + currentRange.end + "/"
                    + currentRange.length);
            ostream.println();

            // Printing content
            exception = copyRange(istream, ostream, currentRange.start,
                    currentRange.end);

            istream.close();

        }

        ostream.println();
        ostream.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    protected void copy(org.apache.naming.resources.CacheEntry cacheEntry, PrintWriter writer,
                        Iterator<Range> ranges, String contentType)
            throws IOException {

        IOException exception = null;

        while ((exception == null) && (ranges.hasNext())) {

            InputStream resourceInputStream = cacheEntry.resource.streamContent();

            Reader reader;
            if (fileEncoding == null) {
                reader = new InputStreamReader(resourceInputStream);
            } else {
                reader = new InputStreamReader(resourceInputStream,
                        fileEncoding);
            }

            Range currentRange = ranges.next();

            // Writing MIME header.
            writer.println();
            writer.println("--" + mimeSeparation);
            if (contentType != null)
                writer.println("Content-Type: " + contentType);
            writer.println("Content-Range: bytes " + currentRange.start
                    + "-" + currentRange.end + "/"
                    + currentRange.length);
            writer.println();

            // Printing content
            exception = copyRange(reader, writer, currentRange.start,
                    currentRange.end);

            reader.close();

        }

        writer.println();
        writer.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param istream The input stream to read from
     * @param ostream The output stream to write to
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(InputStream istream,
                                    ServletOutputStream ostream) {

        // Copy the input stream to the output stream
        IOException exception = null;
        byte buffer[] = new byte[input];
        int len;
        while (true) {
            try {
                len = istream.read(buffer);
                if (len == -1)
                    break;
                ostream.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param reader The reader to read from
     * @param writer The writer to write to
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(Reader reader, PrintWriter writer) {

        // Copy the input stream to the output stream
        IOException exception = null;
        char buffer[] = new char[input];
        int len;
        while (true) {
            try {
                len = reader.read(buffer);
                if (len == -1)
                    break;
                writer.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param istream The input stream to read from
     * @param ostream The output stream to write to
     * @param start   Start of the range which will be copied
     * @param end     End of the range which will be copied
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(InputStream istream,
                                    ServletOutputStream ostream,
                                    long start, long end) {

        if (debug > 10)
            log("Serving bytes:" + start + "-" + end);

        try {
            istream.skip(start);
        } catch (IOException e) {
            return e;
        }

        IOException exception = null;
        long bytesToRead = end - start + 1;

        byte buffer[] = new byte[input];
        int len = buffer.length;
        while ((bytesToRead > 0) && (len >= buffer.length)) {
            try {
                len = istream.read(buffer);
                if (bytesToRead >= len) {
                    ostream.write(buffer, 0, len);
                    bytesToRead -= len;
                } else {
                    ostream.write(buffer, 0, (int) bytesToRead);
                    bytesToRead = 0;
                }
            } catch (IOException e) {
                exception = e;
                len = -1;
            }
            if (len < buffer.length)
                break;
        }

        return exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param reader The reader to read from
     * @param writer The writer to write to
     * @param start  Start of the range which will be copied
     * @param end    End of the range which will be copied
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(Reader reader, PrintWriter writer,
                                    long start, long end) {

        try {
            reader.skip(start);
        } catch (IOException e) {
            return e;
        }

        IOException exception = null;
        long bytesToRead = end - start + 1;

        char buffer[] = new char[input];
        int len = buffer.length;
        while ((bytesToRead > 0) && (len >= buffer.length)) {
            try {
                len = reader.read(buffer);
                if (bytesToRead >= len) {
                    writer.write(buffer, 0, len);
                    bytesToRead -= len;
                } else {
                    writer.write(buffer, 0, (int) bytesToRead);
                    bytesToRead = 0;
                }
            } catch (IOException e) {
                exception = e;
                len = -1;
            }
            if (len < buffer.length)
                break;
        }

        return exception;

    }


    // ------------------------------------------------------ Range Inner Class


    protected class Range {

        public long start;
        public long end;
        public long length;

        /**
         * Validate range.
         */
        public boolean validate() {
            if (end >= length)
                end = length - 1;
            return ((start >= 0) && (end >= 0) && (start <= end)
                    && (length > 0));
        }

        public void recycle() {
            start = 0;
            end = 0;
            length = 0;
        }

    }


}

package ru.dz.blvp;

import freemarker.ext.servlet.FreemarkerServlet;
import freemarker.ext.servlet.IncludePage;
import freemarker.template.SimpleHash;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Map;

/**
 * Created by admin on 2/27/14.
 */
public class SimpleFreeMarkerView extends FreeMarkerView {
    @Override
    protected void doRender(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        //Expose model to JSP tags (as request attributes).
        exposeModelAsRequestAttributes(model, request);

        // Expose all standard FreeMarker hash models.
        SimpleHash fmModel = buildTemplateModel(model, request, response);
        // add the include_page directive
        fmModel.put( FreemarkerServlet.KEY_INCLUDE, new IncludePage( request, response));

        if (logger.isDebugEnabled()) {
            logger.debug("Rendering FreeMarker template [" + getUrl() + "] in FreeMarkerView '" + getBeanName() + "'");
        }

        // Grab the locale-specific version of the template.
        Locale locale = RequestContextUtils.getLocale(request);
        processTemplate(getTemplate(locale), fmModel, response);
    }
}

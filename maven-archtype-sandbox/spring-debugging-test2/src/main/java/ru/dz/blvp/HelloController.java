package ru.dz.blvp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

@Controller
public class HelloController {

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String index(ModelMap model) {
		return "index";
	}
    @RequestMapping(value = "/modules/region", method = RequestMethod.GET)
    public String renderChangeRegion(@CookieValue(required = false) String region, ModelMap modelMap) {
        if (region != null)
            modelMap.addAttribute("region", region);
        return "city";
    }

    @RequestMapping(value = "/modules/region/clear", method = RequestMethod.POST)
    public String clearRegion(@CookieValue(required = false) String region, HttpServletResponse response){
        if (region != null) {
            final Cookie regionCookie = new Cookie("region", "");
            regionCookie.setMaxAge(0);
            regionCookie.setPath("/");
            response.addCookie(regionCookie);
        }
        return "redirect:/";
    }
    @RequestMapping(value = "/modules/region", method = RequestMethod.POST)
    public String processChangeRegion(HttpServletResponse response, @RequestParam String regionId) {
        final Cookie region = new Cookie("region", regionId);
        region.setPath("/");
        response.addCookie(region);
        return "redirect:/";
    }
}
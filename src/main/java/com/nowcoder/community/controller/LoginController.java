package com.nowcoder.community.controller;

import com.google.code.kaptcha.Producer;
import com.nowcoder.community.dao.UserMapper;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.LoginService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.MailClient;
import com.nowcoder.community.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.thymeleaf.TemplateEngine;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
public class LoginController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(MailClient.class);

    @Autowired
    private UserService userService;

    @Autowired
    private Producer kaptchaProducer;

    @Autowired
    private LoginService loginService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${server.servlet.context-path}")
    private String context_path;

    @RequestMapping(path = "/register", method = RequestMethod.GET)
    public String getRegisterPage() {
        return "site/register";
    }

    @RequestMapping(path = "/register", method = RequestMethod.POST)
    public String register(Model model, User user) {
        Map<String, Object> map = userService.register(user);
        if (map == null || map.isEmpty()) {
            model.addAttribute("msg", "??????????????????????????????????????????????????????????????????????????????????????????");
            model.addAttribute("target", "/index");
            return "/site/operate-result";
        } else {
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            model.addAttribute("emailMsg", map.get("emailMsg"));
            return "site/register";
        }
    }

    //url??????????????????http://localhost:8080/community/activation/101/code    #101-??????id???#code-?????????
    @RequestMapping(path="/activation/{userId}/{code}",method = RequestMethod.GET)
    public String activation(Model model,
                             @PathVariable("userId")int userId,
                             @PathVariable("code") String code){
        int result = userService.activation(userId, code);
        if(result==ACTIVATION_SUCCESS){
            model.addAttribute("msg","???????????????????????????????????????");
            model.addAttribute("target","/login");
        }else if(result==ACTIVATION_REPEAT){
            model.addAttribute("msg","????????????????????????????????????");
            model.addAttribute("target","/login");
        }else{
            model.addAttribute("msg","????????????????????????????????????????????????");
            model.addAttribute("target","/register");
        }
        return "site/operate-result";
    }

    @RequestMapping(value = "/login",method = RequestMethod.GET)
    public String getLoginPage(Model model){
        return "site/login";
    }

    @RequestMapping(path = "/kaptcha", method = RequestMethod.GET)
    public void getKaptcha(HttpServletResponse response/*, HttpSession session*/) {
        // ???????????????
        String text = kaptchaProducer.createText();
        BufferedImage image = kaptchaProducer.createImage(text);

        // ??????????????????session
        // session.setAttribute("kaptcha", text);

        // ??????????????????
        String kaptchaOwner = CommunityUtil.generateUUID();
        Cookie cookie = new Cookie("kaptchaOwner", kaptchaOwner);
        //??????????????????10min
        cookie.setMaxAge(60*10);
        cookie.setPath(context_path);
        response.addCookie(cookie);
        // ??????????????????Redis,??????????????????10min
        String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
        redisTemplate.opsForValue().set(redisKey, text, 60*10, TimeUnit.SECONDS);

        // ???????????????????????????
        response.setContentType("image/png");
        try {
            OutputStream os = response.getOutputStream();
            ImageIO.write(image, "png", os);
        } catch (IOException e) {
            logger.error("?????????????????????:" + e.getMessage());
        }
    }

    @RequestMapping(value = "/login",method = RequestMethod.POST)
    public String Login(String username,String password,String code,boolean rememberMe,
                        Model model,@CookieValue("kaptchaOwner") String kaptchaOwner,
                        HttpSession session,
                        HttpServletResponse response){
//        String kaptcha = (String)session.getAttribute("kaptcha");
        String kaptcha=null;
        try {
            if (StringUtils.isNotBlank(kaptchaOwner)) {
                String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
                kaptcha = (String) redisTemplate.opsForValue().get(redisKey);
            }
        }catch (Exception e) {
            model.addAttribute("codeMsg", "???????????????!");
            return "/site/login";
        }

        //??????????????? ??? ?????????????????????????????? ??????????????????????????????????????????
        if(StringUtils.isBlank(kaptcha)||StringUtils.isBlank(code)||!StringUtils.equalsIgnoreCase(code,kaptcha)){
            model.addAttribute("codeMsg","??????????????????");
            return "site/login";
        }
        //?????????????????????
        int expiredSeconds = rememberMe?REMEMBER_EXPIRED_SECONDS:DEFAULT_EXPIRED_SECONDS;
        Map<String, Object> msg = loginService.login(username, password, expiredSeconds);
        if(msg.containsKey("ticket")){
            Cookie cookie = new Cookie("ticket",(String)msg.get("ticket"));
            cookie.setPath(context_path);
            cookie.setMaxAge(expiredSeconds);
            response.addCookie(cookie);
            //??????????????????
            return "redirect:/index";
        }else{
            model.addAttribute("usernameMsg",msg.get("usernameMsg"));
            model.addAttribute("passwordMsg",msg.get("passwordMsg"));
            return "site/login";
        }
    }

    //??????
    @RequestMapping(path="/logout",method = RequestMethod.GET)
    public String logout(@CookieValue("ticket") String ticket){
        loginService.logout(ticket);
        return "redirect:/login";
    }
}

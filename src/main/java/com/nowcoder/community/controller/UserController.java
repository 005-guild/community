package com.nowcoder.community.controller;

import com.nowcoder.community.annotation.LoginRequired;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.FollowService;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController implements CommunityConstant {

    @Autowired
    UserService userService;
    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Value("${community.path.domain}")
    private String domain;
    @Value("${community.path.upload}")
    private String uploadPath;
    @Autowired
    private HostHolder hostHolder;
    @Autowired
    private LikeService likeService;
    @Autowired
    private FollowService followService;

    private Logger logger = LoggerFactory.getLogger(UserController.class);

    @LoginRequired
    @RequestMapping(path = "/setting", method = RequestMethod.GET)
    public String getSettingPage() {
        return "/site/setting";
    }

    /**
     * ???????????????????????????
     * @param multipartFile
     * @param model
     * @return
     */
    @LoginRequired
    @RequestMapping(path="/upload",method = RequestMethod.POST)
    public String uploadHeader(MultipartFile multipartFile, Model model){
        if(multipartFile==null){
            model.addAttribute("error","???????????????????????????");
            return "/site/setting";
        }
        String fileName = multipartFile.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        //System.out.println(suffix.equals(".jpg"));
        if(StringUtils.isBlank(suffix)||!(suffix.equals(".png")||suffix.equals(".jpg"))){
            model.addAttribute("error","????????????????????????????????????");
            return "/site/setting";
        }
        fileName = CommunityUtil.generateUUID()+suffix;
        File dst = new File(uploadPath+"/"+fileName);
        try {
            multipartFile.transferTo(dst);
        } catch (IOException e) {
            logger.error("???????????????"+e.getMessage());
            throw new RuntimeException("??????????????????????????????????????????"+e);
        }
        //??????headerUrl???????????????????????????
        //http://localhost:8080/community/user/header/filename
        String headerUrl = domain+contextPath+"/user/header/"+fileName;
        User user = hostHolder.getUser();
        userService.updateHeader(user.getId(),headerUrl);
        return "redirect:/user/setting";
    }
    /**
     * ???????????????headerUrl???????????????????????????
     * @param filename
     * @param response
     */
    @RequestMapping(path = "/header/{filename}",method = RequestMethod.GET)
    public void getImg(@PathVariable("filename")String filename, HttpServletResponse response){
        //?????????????????????
        filename = uploadPath+"/"+filename;
        try (ServletOutputStream os = response.getOutputStream();
             InputStream is = new FileInputStream(filename);)
        {
            int len = 0;
            byte[] buffer = new byte[1024];
            while((len=is.read(buffer))!=-1){
                os.write(buffer,0,len);
            }
        } catch (IOException e) {
            logger.error("??????????????????:"+e.getMessage());
        }
    }

    @LoginRequired
    @RequestMapping(path = "/changePassword", method = {RequestMethod.GET,RequestMethod.POST})
    //???????????????model?????????????????????????????????
    public String changePassword(String oldPassword,String newPassword,String confirmPassword, Model model) {
        User user = hostHolder.getUser();
        Map<String, Object> map = userService.changePassword(user,oldPassword, newPassword, confirmPassword);
        if(map == null || map.isEmpty()){
            return "redirect:/index";
        }else {
            model.addAttribute("oldPasswordMsg", map.get("oldPasswordMsg"));
            model.addAttribute("newPasswordMsg", map.get("newPasswordMsg"));
            model.addAttribute("confirmPasswordMsg", map.get("confirmPasswordMsg"));
            return "/site/setting";
        }
    }

    // ????????????
    @RequestMapping(path = "/profile/{userId}", method = RequestMethod.GET)
    public String getProfilePage(@PathVariable("userId") int userId, Model model,
                                 @RequestParam(name = "infoMode", defaultValue = "0") int infoMode) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("??????????????????!");
        }

        // ??????
        model.addAttribute("user", user);
        // ????????????
        int likeCount = likeService.findUserLikeCount(userId);
        model.addAttribute("likeCount", likeCount);

        // ????????????
        long followeeCount = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        model.addAttribute("followeeCount", followeeCount);
        // ????????????
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        model.addAttribute("followerCount", followerCount);
        // ???????????????
        boolean hasFollowed = false;
        if (hostHolder.getUser() != null) {
            hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
        }
        model.addAttribute("hasFollowed", hasFollowed);
        model.addAttribute("infoMode", infoMode);

        return "/site/profile";
    }
}

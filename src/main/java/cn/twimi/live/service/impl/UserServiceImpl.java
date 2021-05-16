package cn.twimi.live.service.impl;

import cn.twimi.live.model.FileInfo;
import cn.twimi.live.service.FileService;
import org.springframework.stereotype.Service;
import cn.twimi.live.common.ApiResponse;
import cn.twimi.live.dao.UserDao;
import cn.twimi.live.model.User;
import cn.twimi.live.service.UserService;
import cn.twimi.live.util.MD5;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    private UserDao userDao;
    private FileService fileService;

    public UserServiceImpl(UserDao userDao, FileService fileService) {
        this.userDao = userDao;
        this.fileService = fileService;
    }

    private String generateToken(String username) {
        return MD5.encode("BUNNY_" + Math.random() + "$" + username);
    }

    @Override
    public User getUserById(long id) {
        return userDao.getById(id);
    }

    @Override
    public User getUserByToken(String token) {
        User user = userDao.getByToken(token);
        if (user != null) {
            if ((new Date()).after(user.getExpire())) {
                return null;
            }
        }
        return user;
    }

    @Override
    public List<User> getUsersBySearch(String search, int page, int size) {
        return userDao.listBy(new HashMap<String, Object>() {{
            put("keyword", search);
        }}, page, size);
    }

    @Override
    public List<User> getUsersByPage(int page, int size) {
        return userDao.listByPage(page, size);
    }

    @Override
    public int countUser() {
        return userDao.count();
    }

    @Override
    public ApiResponse<Boolean> updateState(long id, int state) {
        if (id == 1) {
            return ApiResponse.<Boolean>builder().status(1006).msg("无法修改管理员").build();
        }
        int res = userDao.updateState(id, state);
        if (res > 0) {
            return ApiResponse.<Boolean>builder().status(0).msg("ok").data(true).build();
        } else {
            return ApiResponse.<Boolean>builder().status(-6).msg("数据库出错").data(false).build();
        }
    }

    @Override
    public ApiResponse<Boolean> updateName(long id, String name) {
        int res = userDao.updateName(id, name);
        if (res > 0) {
            return ApiResponse.<Boolean>builder().status(0).msg("ok").data(true).build();
        } else {
            return ApiResponse.<Boolean>builder().status(-6).msg("数据库出错").data(false).build();
        }
    }

    @Override
    public ApiResponse<String> updateAvatar(long id, MultipartFile file) {
        FileInfo fileInfo = fileService.upload(file, "avatar");
        String path = fileService.pathToUrl(fileInfo.getPath());
        int res = userDao.updateAvatar(id, path);
        if (res > 0) {
            return ApiResponse.<String>builder().status(0).msg("ok").data(path).build();
        } else {
            return ApiResponse.<String>builder().status(-6).msg("数据库出错").build();
        }
    }

    @Override
    public ApiResponse<User> login(String username, String password) {
        User user = userDao.getByUsername(username);
        if (user == null) {
            return ApiResponse.<User>builder().status(1002).msg("用户不存在").build();
        }
        if (!user.getPassword().equals(MD5.encode(password))) {
            return ApiResponse.<User>builder().status(1001).msg("密码错误").build();
        }
        if (user.getState() != 1) {
            if (user.getState() == 0) {
                return ApiResponse.<User>builder().status(1005).msg("账号未启用").build();
            }
            if (user.getState() == 2) {
                return ApiResponse.<User>builder().status(1004).msg("账号已被禁用").build();
            }
        }
        if (null == user.getExpire() || (new Date()).after(user.getExpire())) {
            user.setExpire(new Date(System.currentTimeMillis() + 86400000L));
            user.setToken(generateToken(user.getUsername()));
        }
        user.setUpdateTime(new Date());
        if (userDao.updateToken(user) > 0) {
            return ApiResponse.<User>builder().status(0).msg("ok").data(user).build();
        } else {
            return ApiResponse.<User>builder().status(-6).msg("数据库出错").build();
        }
    }

    @Override
    public ApiResponse<User> register(User user) {
        User u = userDao.getByUsername(user.getUsername());
        if (u != null) {
            return ApiResponse.<User>builder().status(1003).msg("用户名已存在").build();
        }
        u = userDao.getByUsername(user.getEmail());
        if (u != null) {
            return ApiResponse.<User>builder().status(1003).msg("邮箱已存在").build();
        }
        user.setPassword(MD5.encode(user.getPassword()));
        user.setExpire(new Date(System.currentTimeMillis() + 86400000L));
        user.setToken(generateToken(user.getUsername()));
        int row = userDao.create(user);
        if (row <= 0) {
            return ApiResponse.<User>builder().status(-6).msg("数据库出错").build();
        }
        return ApiResponse.<User>builder().status(0).msg("ok").data(user).build();
    }
}

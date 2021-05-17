package com.itiger.persona.controller;

import com.itiger.persona.entity.CatalogInfo;
import com.itiger.persona.entity.request.CatalogInfoRequest;
import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.service.ICatalogInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @Author Shik
 * @Title: CatalogInfoController
 * @ProjectName: persona-platform-backend
 * @Description: TODO
 * @Date: 2021/5/13 下午2:06
 */
@RestController
@RequestMapping("/catalog-info")
public class CatalogInfoController {

    @Autowired
    private ICatalogInfoService iCatalogInfoService;

    @GetMapping
    public ResultInfo list(CatalogInfoRequest catalogInfoRequest,
                           HttpServletRequest request) {

        List<CatalogInfo> list = this.iCatalogInfoService.list();

        return ResultInfo.success(list);

    }

}

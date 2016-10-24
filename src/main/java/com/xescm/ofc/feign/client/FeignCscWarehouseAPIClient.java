package com.xescm.ofc.feign.client;

import com.xescm.ofc.config.RestConfig;
import com.xescm.ofc.domain.OfcWarehouseInformation;
import com.xescm.ofc.exception.BusinessException;
import com.xescm.ofc.feign.api.FeignCscSupplierAPI;
import com.xescm.ofc.feign.api.FeignCscWarehouseAPI;
import com.xescm.uam.domain.feign.AuthRequestInterceptor;
import com.xescm.uam.utils.wrap.Wrapper;
import feign.Feign;
import feign.Param;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Created by lyh on 2016/10/19.
 */
@Service
public class FeignCscWarehouseAPIClient {
    private static final Logger logger = LoggerFactory.getLogger(FeignCscWarehouseAPI.class);
    @Resource
    RestConfig restConfig;

    public FeignCscWarehouseAPI getApi() {
        FeignCscWarehouseAPI res = Feign.builder()
                .requestInterceptor(new AuthRequestInterceptor()).encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder()).target(FeignCscWarehouseAPI.class, restConfig.getCscUrl());
        return res;
    }
    Wrapper<?> getCscWarehouseByCustomerId(@Param("customerId") String customerId){
        logger.debug("==>通过客户编码取仓库 customerId={}", customerId);
        if(null == customerId){
            throw new BusinessException("参数为空");
        }
        Wrapper<?> cscWarehouseByCustomerId = getApi().getCscWarehouseByCustomerId(customerId);
        return cscWarehouseByCustomerId;
    }
    public Wrapper<OfcWarehouseInformation> getRmcWarehouseByid(@Param("id") String id){
        logger.debug("==>通过客户编码取仓库 id={}", id);
        if(null == id){
            throw new BusinessException("参数为空");
        }
        Wrapper<OfcWarehouseInformation> rmcWarehouseByid = getApi().getRmcWarehouseByid(id);
        return rmcWarehouseByid;
    }
}

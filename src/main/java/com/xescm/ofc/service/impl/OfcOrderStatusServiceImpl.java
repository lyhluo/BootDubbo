package com.xescm.ofc.service.impl;

import com.xescm.core.utils.PubUtils;
import com.xescm.ofc.domain.OfcOrderNewstatus;
import com.xescm.ofc.domain.OfcOrderStatus;
import com.xescm.ofc.exception.BusinessException;
import com.xescm.ofc.mapper.OfcOrderNewstatusMapper;
import com.xescm.ofc.mapper.OfcOrderStatusMapper;
import com.xescm.ofc.service.OfcOrderNewstatusService;
import com.xescm.ofc.service.OfcOrderStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xescm.ofc.constant.OrderConstConstant.*;

/**
 * Created by lyh on 2016/10/10.
 */
@Service
@Transactional
public class OfcOrderStatusServiceImpl extends BaseService<OfcOrderStatus> implements OfcOrderStatusService {
    private static final Logger logger = LoggerFactory.getLogger(OfcOrderStatusServiceImpl.class);
    @Resource
    private OfcOrderStatusMapper ofcOrderStatusMapper;
    @Resource
    private OfcOrderNewstatusMapper ofcOrderNewstatusMapper;
    @Resource
    private OfcOrderNewstatusService ofcOrderNewstatusService;

    @Override
    public int deleteByOrderCode(Object key) {
        return ofcOrderStatusMapper.deleteByOrderCode(key);
    }

    @Override
    public List<OfcOrderStatus> orderStatusScreen(String code, String followTag) {
        if (!PubUtils.trimAndNullAsEmpty(code).equals("")) {
            String orderCode = null;
            String custOrderCode = null;
            String transCode = null;
            switch (followTag) {
                case "orderCode":
                    orderCode = code;
                    break;
                case "custOrderCode":
                    custOrderCode = code;
                    break;
                case "transCode":
                    transCode = code;
                    break;
            }
            // Map<String,String> mapperMap = new HashMap<String,String>();
            Map<String, String> mapperMap = new HashMap<>();
            mapperMap.put("orderCode", orderCode);
            mapperMap.put("custOrderCode", custOrderCode);
            mapperMap.put("transCode", transCode);
            return ofcOrderStatusMapper.orderStatusScreen(mapperMap);
        } else {
            throw new BusinessException("订单状态查询有误");
        }
    }

    @Override
    public OfcOrderStatus orderStatusSelect(String code, String followTag) {
        if (!PubUtils.trimAndNullAsEmpty(code).equals("")) {
            String orderCode = null;
            String custOrderCode = null;
            String transCode = null;

            switch (followTag) {
                case "orderCode":
                    orderCode = code;
                    break;
                case "custOrderCode":
                    custOrderCode = code;
                    break;
                case "transCode":
                    transCode = code;
                    break;
            }
            // Map<String,String> mapperMap = new HashMap<String,String>();
            Map<String, String> mapperMap = new HashMap<>();
            mapperMap.put("orderCode", orderCode);
            mapperMap.put("custOrderCode", custOrderCode);
            mapperMap.put("transCode", transCode);
            OfcOrderNewstatus orderNewstatus=ofcOrderNewstatusMapper.orderStatusSelectNew(mapperMap);
            OfcOrderStatus ofcOrderStatus = ofcOrderStatusMapper.orderStatusSelect(mapperMap);
            if(orderNewstatus==null
                    || PubUtils.trimAndNullAsEmpty(orderNewstatus.getOrderCode()).equals("")
                    || PubUtils.trimAndNullAsEmpty(orderNewstatus.getOrderLatestStatus()).equals("")){

            }else{
                ofcOrderStatus.setOrderCode(orderNewstatus.getOrderCode());
                ofcOrderStatus.setOrderStatus(orderNewstatus.getOrderLatestStatus());
            }
            return ofcOrderStatus;
        } else {
            throw new BusinessException("订单状态查询有误");
        }

    }

    @Override
    public OfcOrderStatus queryOrderStateByOrderCode(String orderCode) {
        return ofcOrderStatusMapper.queryOrderStateByOrderCode(orderCode);
    }

    @Override
    public void cancelOrderStateByOrderCode(String orderCode) {
        OfcOrderNewstatus orderNewstatus=new OfcOrderNewstatus();
        orderNewstatus.setOrderCode(orderCode);
        orderNewstatus.setOrderLatestStatus(HASBEENCANCELED);
        orderNewstatus.setStatusUpdateTime(new Date());
        ofcOrderNewstatusService.update(orderNewstatus);
        ofcOrderStatusMapper.cancelOrderStateByOrderCode(orderCode);
    }

    @Override
    public OfcOrderStatus queryLastUpdateOrderByOrderCode(String orderCode) {
        OfcOrderNewstatus orderNewstatus=ofcOrderNewstatusService.selectByKey(orderCode);
        OfcOrderStatus ofcOrderStatus=new OfcOrderStatus();
        if(orderNewstatus==null
                || PubUtils.trimAndNullAsEmpty(orderNewstatus.getOrderCode()).equals("")
                || PubUtils.trimAndNullAsEmpty(orderNewstatus.getOrderLatestStatus()).equals("")){
            ofcOrderStatus = ofcOrderStatusMapper.queryLastUpdateOrderByOrderCode(orderCode);
        }else{
            ofcOrderStatus.setOrderCode(orderNewstatus.getOrderCode());
            ofcOrderStatus.setOrderStatus(orderNewstatus.getOrderLatestStatus());
        }
        return ofcOrderStatus;
    }

    public OfcOrderStatus queryLastTimeOrderByOrderCode(String orderCode) {
        OfcOrderNewstatus orderNewstatus=ofcOrderNewstatusService.selectByKey(orderCode);
        OfcOrderStatus ofcOrderStatus=new OfcOrderStatus();
        if(orderNewstatus==null
                || PubUtils.trimAndNullAsEmpty(orderNewstatus.getOrderCode()).equals("")
                || PubUtils.trimAndNullAsEmpty(orderNewstatus.getOrderLatestStatus()).equals("")){
            ofcOrderStatus = ofcOrderStatusMapper.queryLastTimeOrderByOrderCode(orderCode);
        }else{
            ofcOrderStatus.setOrderCode(orderNewstatus.getOrderCode());
            ofcOrderStatus.setOrderStatus(orderNewstatus.getOrderLatestStatus());
        }
        return ofcOrderStatus;
    }

    @Override
    public int save(OfcOrderStatus ofcOrderStatus) {
        if (ofcOrderStatus!=null
                && !PubUtils.trimAndNullAsEmpty(ofcOrderStatus.getOrderCode()).equals("")){
            if(!PubUtils.trimAndNullAsEmpty(ofcOrderStatus.getOrderStatus()).equals("")){
                OfcOrderNewstatus orderNewstatus=ofcOrderNewstatusService.selectByKey(ofcOrderStatus.getOrderCode());
                String tag="noStatus";
                if(orderNewstatus!=null){
                    tag="haveStatus";
                }else {
                    orderNewstatus=new OfcOrderNewstatus();
                }
                if(ofcOrderStatus.getOrderStatus().equals(IMPLEMENTATIONIN)){
                    if(!PubUtils.trimAndNullAsEmpty(orderNewstatus.getOrderLatestStatus()).equals(HASBEENCOMPLETED)){
                        updateOrderNewStatus(ofcOrderStatus,tag);
                    }
                }else{
                    updateOrderNewStatus(ofcOrderStatus,tag);
                }
            }else {
                throw new BusinessException("订单状态为空，保存订单状态失败");
            }
        }
        return super.save(ofcOrderStatus);
    }

    public void updateOrderNewStatus(OfcOrderStatus ofcOrderStatus,String tag){
        OfcOrderNewstatus orderNewstatus=new OfcOrderNewstatus();
        orderNewstatus.setOrderCode(ofcOrderStatus.getOrderCode());
        orderNewstatus.setOrderLatestStatus(ofcOrderStatus.getOrderStatus());
        if(tag.equals("haveStatus")){
            orderNewstatus.setStatusUpdateTime(new Date());
            ofcOrderNewstatusService.update(orderNewstatus);
        }else if(tag.equals("noStatus")){
            orderNewstatus.setStatusUpdateTime(new Date());
            orderNewstatus.setStatusCreateTime(new Date());
            ofcOrderNewstatusService.save(orderNewstatus);
        }
    }
}

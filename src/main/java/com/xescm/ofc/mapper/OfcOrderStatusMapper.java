package com.xescm.ofc.mapper;

import com.xescm.ofc.domain.OfcOrderStatus;
import com.xescm.ofc.domain.OrderScreenCondition;
import com.xescm.ofc.domain.OrderScreenResult;
import com.xescm.ofc.utils.MyMapper;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface OfcOrderStatusMapper extends MyMapper<OfcOrderStatus> {
    int deleteByOrderCode(Object key);
    List<OfcOrderStatus> orderStatusScreen(Object key);
}
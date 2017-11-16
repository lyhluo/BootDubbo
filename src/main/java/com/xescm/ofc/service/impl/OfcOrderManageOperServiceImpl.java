package com.xescm.ofc.service.impl;

import com.xescm.base.model.dto.auth.AuthResDto;
import com.xescm.base.model.wrap.Wrapper;
import com.xescm.core.utils.PubUtils;
import com.xescm.ofc.domain.*;
import com.xescm.ofc.exception.BusinessException;
import com.xescm.ofc.mapper.OfcOrderOperMapper;
import com.xescm.ofc.mapper.OfcOrderScreenMapper;
import com.xescm.ofc.model.dto.form.OrderOperForm;
import com.xescm.ofc.model.dto.form.OrderStorageOperForm;
import com.xescm.ofc.model.dto.ofc.OfcOrderInfoDTO;
import com.xescm.ofc.model.dto.ofc.OfcQueryStorageDTO;
import com.xescm.ofc.model.vo.ofc.OfcGroupVo;
import com.xescm.ofc.service.*;
import com.xescm.tfc.edas.model.dto.DeliverDetailsOrdeDto;
import com.xescm.tfc.edas.service.AcGetDeliveryOrderEdasService;
import com.xescm.uam.model.dto.group.UamGroupDto;
import com.xescm.uam.provider.UamGroupEdasService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

import static com.xescm.ofc.constant.OrderConstConstant.*;

/**
 * 运营中心订单管理
 * Created by hiyond on 2016/11/24.
 */
@Service
public class OfcOrderManageOperServiceImpl implements OfcOrderManageOperService {

    Logger logger = LoggerFactory.getLogger(OfcOrderManageOperServiceImpl.class);

    @Resource
    private UamGroupEdasService uamGroupEdasService;
    @Resource
    private OfcOrderScreenMapper ofcOrderScreenMapper;
    @Resource
    private OfcOrderOperMapper ofcOrderOperMapper;
    @Resource
    private OfcOrderManageOperService ofcOrderManageOperService;
    @Resource
    private OfcFundamentalInformationService ofcFundamentalInformationService;
    @Resource
    private OfcDistributionBasicInfoService ofcDistributionBasicInfoService;
    @Resource
    private OfcFinanceInformationService ofcFinanceInformationService;
    @Resource
    private OfcGoodsDetailsInfoService ofcGoodsDetailsInfoService;
    @Resource
    private OfcWarehouseInformationService ofcWarehouseInformationService;
    @Resource
    private OrderFollowOperService orderFollowOperService;
    @Resource
    private OfcOrderNewstatusService ofcOrderNewstatusService;

    @Resource
    private AcGetDeliveryOrderEdasService acGetDeliveryOrderEdasService;

    @Override
    public List<OrderSearchOperResult> queryOrderStorageDataOper(AuthResDto authResDto, OfcQueryStorageDTO ofcQueryStorageDTO) {
        if (IN.equals(ofcQueryStorageDTO.getTag())) {
            ofcQueryStorageDTO.setBusinessTypes(Arrays.asList(INBUSINESSTYPES));
        } else if (OUT.equals(ofcQueryStorageDTO.getTag())) {
            ofcQueryStorageDTO.setBusinessTypes(Arrays.asList(OUTBUSINESSTYPES));
        }
        OrderStorageOperForm form = new OrderStorageOperForm();
        BeanUtils.copyProperties(ofcQueryStorageDTO,form);
        return queryStorageOrderList(authResDto,form);
    }


    private List<OrderSearchOperResult> queryStorageOrderList(AuthResDto authResDto,OrderStorageOperForm form) {
        //订单管理筛选后端权限校验
        if (null == authResDto || null == form) {
            throw new BusinessException("订单管理筛选后端权限校验入参有误");
        }
        List<OrderSearchOperResult> orderSearchOperResults;
        UamGroupDto uamGroupDto = new UamGroupDto();
        uamGroupDto.setSerialNo(authResDto.getGroupRefCode());
        String userId = authResDto.getUserId();
        Wrapper<List<UamGroupDto>> allGroupByType = uamGroupEdasService.getAllGroupByType(uamGroupDto);
        ofcOrderManageOperService.checkUamGroupEdasResultNullOrError(allGroupByType);
        if (CollectionUtils.isEmpty(allGroupByType.getResult()) || allGroupByType.getResult().size() > 1) {
            throw new BusinessException("查询当前登录用户组织信息出错:查询到的结果为空或有误");
        }
        UamGroupDto uamGroupDtoResult = allGroupByType.getResult().get(0);
        if (null == uamGroupDtoResult || PubUtils.isSEmptyOrNull(uamGroupDtoResult.getType())) {
            throw new BusinessException("查询当前登录用户组织信息出错:查询到的结果有误");
        }
        String userGroupCode = authResDto.getGroupRefCode();
        String areaSerialNo = form.getAreaSerialNo();
        String baseSerialNo = form.getBaseSerialNo();
        if (PubUtils.isSEmptyOrNull(userGroupCode)) {
            throw new BusinessException("当前登录的用户没有流水号!");
        }
        String groupType = uamGroupDtoResult.getType();
        if (PubUtils.isSEmptyOrNull(areaSerialNo) && !PubUtils.isSEmptyOrNull(baseSerialNo)) {
            throw new BusinessException("基地所属大区未选择!");
        }
        if (StringUtils.equals(groupType,"1")) {
            //鲜易供应链身份
            if (StringUtils.equals("GD1625000003",uamGroupDtoResult.getSerialNo())) {
                orderSearchOperResults = ofcOrderOperMapper.queryStorageOrderList(form, null, false);
                //大区身份
            } else {
                orderSearchOperResults = this.queryStorageOrderListOfArea(form, areaSerialNo, baseSerialNo, userGroupCode, userId);
            }
            //基地身份
        } else if (StringUtils.equals(groupType,"3")) {
            orderSearchOperResults = this.queryStorageOrderListOfBase(form, areaSerialNo, baseSerialNo, userGroupCode, userId);
            //仓库身份, 其他身份
        } else {
            orderSearchOperResults = ofcOrderOperMapper.queryStorageOrderList(form, userId, true);
        }
        return orderSearchOperResults;
    }

    private List<OrderSearchOperResult> queryStorageOrderListOfArea(OrderStorageOperForm form, String areaSerialNo, String baseSerialNo, String userGroupCode, String userId) {
        List<OrderSearchOperResult> results;
        boolean areaEmpty = PubUtils.isSEmptyOrNull(areaSerialNo);
        boolean baseEmpty = PubUtils.isSEmptyOrNull(baseSerialNo);
        boolean areaEqualUser = StringUtils.equals(userGroupCode, areaSerialNo);
        if (areaEmpty && baseEmpty) {
            form.setAreaSerialNo(userGroupCode);
            results = ofcOrderOperMapper.queryStorageOrderListPrecise(form, userId, false);
        } else if (!areaEqualUser && !areaEmpty) {
            results = ofcOrderOperMapper.queryStorageOrderList(form, userId, true);
        } else {
            results = ofcOrderOperMapper.queryStorageOrderList(form, null, null);
        }
        return results;
    }

    private List<OrderSearchOperResult> queryStorageOrderListOfBase(OrderStorageOperForm form, String areaSerialNo, String baseSerialNo, String userGroupCode, String userId) {
        List<OrderSearchOperResult> results;
        //反查大区
        UamGroupDto groupDto = new UamGroupDto();
        groupDto.setSerialNo(userGroupCode);
        OfcGroupVo ofcGroupVo = this.queryAreaMsgByBase(groupDto);
        String userAreaSerialNo = ofcGroupVo.getSerialNo();
        boolean areaEmpty = PubUtils.isSEmptyOrNull(areaSerialNo);
        boolean baseEmpty = PubUtils.isSEmptyOrNull(baseSerialNo);
        boolean areaEqualUser = StringUtils.equals(userAreaSerialNo, areaSerialNo);
        boolean baseEqualUser = StringUtils.equals(userGroupCode, baseSerialNo);
        if (areaEmpty && baseEmpty) {
            form.setAreaSerialNo(userAreaSerialNo);
            form.setBaseSerialNo(userGroupCode);
            results = ofcOrderOperMapper.queryStorageOrderListPrecise(form, userId, false);
        } else if (areaEqualUser && baseEqualUser) {
            results = ofcOrderOperMapper.queryStorageOrderList(form, null, null);
        } else if (areaEqualUser && baseEmpty) {
            results = ofcOrderOperMapper.queryStorageOrderListUnion(form, userId, true, userGroupCode);
        } else {
            results = ofcOrderOperMapper.queryStorageOrderList(form, userId, true);
        }
        return results;
    }
    @Override
    public List<OrderScreenResult> queryOrderOper(OrderOperForm form) {
        return ofcOrderScreenMapper.queryOrderOper(form);
    }

    /**
     * 订单管理筛选，以及后端权限校验
     * @param authResDto 当前登录用户信息
     * @param form 查询条件
     * @return
     */
    @Override
    public List<OrderSearchOperResult> queryOrderList(AuthResDto authResDto,OrderOperForm form) {
        if (null == authResDto || null == form) {
            throw new BusinessException("订单管理筛选后端权限校验入参有误");
        }
        List<OrderSearchOperResult> orderSearchOperResults = new ArrayList<>();
        String userId = authResDto.getUserId();
        UamGroupDto uamGroupDto = new UamGroupDto();
        uamGroupDto.setSerialNo(authResDto.getGroupRefCode());
        Wrapper<List<UamGroupDto>> allGroupByType = uamGroupEdasService.getAllGroupByType(uamGroupDto);
        ofcOrderManageOperService.checkUamGroupEdasResultNullOrError(allGroupByType);
        if (CollectionUtils.isEmpty(allGroupByType.getResult()) || allGroupByType.getResult().size() > 1) {
            throw new BusinessException("查询当前登录用户组织信息出错:查询到的结果为空或有误");
        }
        UamGroupDto uamGroupDtoResult = allGroupByType.getResult().get(0);
        if (null == uamGroupDtoResult || PubUtils.isSEmptyOrNull(uamGroupDtoResult.getType())) {
            throw new BusinessException("查询当前登录用户组织信息出错:查询到的结果有误");
        }
        String userGroupCode = authResDto.getGroupRefCode();
        if (PubUtils.isSEmptyOrNull(userGroupCode)) {
            throw new BusinessException("当前登录的用户没有所属组织!");
        }
        String groupType = uamGroupDtoResult.getType();
        String areaSerialNo = form.getAreaSerialNo();
        String baseSerialNo = form.getBaseSerialNo();
        if (PubUtils.isSEmptyOrNull(areaSerialNo) && !PubUtils.isSEmptyOrNull(baseSerialNo)) {
            throw new BusinessException("基地所属大区未选择!");
        }
        // 2017.8.17 订单状态支持多选
        if (!PubUtils.isSEmptyOrNull(form.getOrderState())){
            String orderStateStr = form.getOrderState();
            String[] orderStateArray = orderStateStr.split(",");
            List<String> strList = new ArrayList<>();
            for (String status:orderStateArray) {
                strList.add(status);
            }
            form.setOrderStateList(strList);
        }
        // 2017年5月12日 追加逻辑 增加创建人查看权限
        if (StringUtils.equals(groupType,"1")) {
            //鲜易供应链身份
            if (StringUtils.equals("GD1625000003",userGroupCode)) {
                orderSearchOperResults = ofcOrderOperMapper.queryOrderList(form, null, false);
                //大区身份
            } else {
                orderSearchOperResults = this.queryOrderListOfArea(form, areaSerialNo, baseSerialNo, userGroupCode, userId);
            }
            //基地身份
        } else if (StringUtils.equals(groupType,"3")) {
            orderSearchOperResults = this.queryOrderListOfBase(form, areaSerialNo, baseSerialNo, userGroupCode, userId);
            //仓库身份, 其他身份
        } else {
            orderSearchOperResults = ofcOrderOperMapper.queryOrderList(form, userId, true);
        }

        return orderSearchOperResults;
    }

    private List<OrderSearchOperResult> queryOrderListOfArea(OrderOperForm form, String areaSerialNo, String baseSerialNo, String userGroupCode, String userId) {
        List<OrderSearchOperResult> results;
        boolean areaEmpty = PubUtils.isSEmptyOrNull(areaSerialNo);
        boolean baseEmpty = PubUtils.isSEmptyOrNull(baseSerialNo);
        boolean areaEqualUser = StringUtils.equals(userGroupCode, areaSerialNo);
        if (areaEmpty && baseEmpty) {
            form.setAreaSerialNo(userGroupCode);
            results = ofcOrderOperMapper.queryOrderListPrecise(form, userId, false);
        } else if (!areaEqualUser && !areaEmpty) {
            results = ofcOrderOperMapper.queryOrderList(form, userId, true);
        } else {
            results = ofcOrderOperMapper.queryOrderList(form, null, null);
        }
        return results;
    }

    private List<OrderSearchOperResult> queryOrderListOfBase(OrderOperForm form, String areaSerialNo, String baseSerialNo, String userGroupCode, String userId) {
        List<OrderSearchOperResult> results;
        //反查大区
        UamGroupDto groupDto = new UamGroupDto();
        groupDto.setSerialNo(userGroupCode);
        OfcGroupVo ofcGroupVo = this.queryAreaMsgByBase(groupDto);
        String userAreaSerialNo = ofcGroupVo.getSerialNo();
        boolean areaEmpty = PubUtils.isSEmptyOrNull(areaSerialNo);
        boolean baseEmpty = PubUtils.isSEmptyOrNull(baseSerialNo);
        boolean areaEqualUser = StringUtils.equals(userAreaSerialNo, areaSerialNo);
        boolean baseEqualUser = StringUtils.equals(userGroupCode, baseSerialNo);
        if (areaEmpty && baseEmpty) {
            form.setAreaSerialNo(userAreaSerialNo);
            form.setBaseSerialNo(userGroupCode);
            results = ofcOrderOperMapper.queryOrderListPrecise(form, userId, false);
        } else if ((areaEqualUser && baseEqualUser)) {
            results = ofcOrderOperMapper.queryOrderList(form, null, null);
        } else if (areaEqualUser && baseEmpty) {
            results = ofcOrderOperMapper.queryOrderListUnion(form, userId, true, userGroupCode);
        } else {
            results = ofcOrderOperMapper.queryOrderList(form, userId, true);
        }
        return results;
    }

    /**
     * 根据订单批次号查询订单
     * @param orderBatchNumber 订单批次号
     * @return
     */
    @Override
    public List<OrderSearchOperResult> queryOrderByOrderBatchNumber(String orderBatchNumber) {
        return ofcOrderOperMapper.queryOrderByOrderBatchNumber(orderBatchNumber);
    }

    /**
     * 订单跟踪根据不同方式查询订单
     * @param code 编码
     * @param searchType 类型
     * @return
     */
    @Override
    public List<OrderFollowOperResult> queryOrder(String code, String searchType) {
        return ofcOrderOperMapper.queryOrder(code, searchType);
    }

    /**
     * 根据当前登录用户, 加载大区基地
     * @param authResDto 当前登录用户
     * @return
     */
    @Override
    public Map<String, List<OfcGroupVo>> queryGroupList(AuthResDto authResDto) {
        UamGroupDto uamGroupDto = new UamGroupDto();
        if (null == authResDto || PubUtils.isSEmptyOrNull(authResDto.getGroupRefCode())) {
            return null;
        }
        uamGroupDto.setSerialNo(authResDto.getGroupRefCode());
        Wrapper<List<UamGroupDto>> allGroupByType = uamGroupEdasService.getAllGroupByType(uamGroupDto);
        checkUamGroupEdasResultNullOrError(allGroupByType);
        if (CollectionUtils.isEmpty(allGroupByType.getResult()) || allGroupByType.getResult().size() > 1) {
            throw new BusinessException("查询当前登录用户组织信息出错:查询到的结果为空或有误");
        }
        UamGroupDto uamGroupDtoResult = allGroupByType.getResult().get(0);
        if (null == uamGroupDtoResult || PubUtils.isSEmptyOrNull(uamGroupDtoResult.getType())) {
            throw new BusinessException("查询当前登录用户组织信息出错:查询到的结果有误");
        }
        if (PubUtils.isSEmptyOrNull(uamGroupDtoResult.getSerialNo())) {
            throw new BusinessException("当前登录的用户没有流水号!");
        }
        Map<String, List<OfcGroupVo>> resultMap;
        String groupType = uamGroupDtoResult.getType();
        if (StringUtils.equals(groupType,"1")) {
            //鲜易供应链身份
            if (StringUtils.equals("GD1625000003",uamGroupDtoResult.getSerialNo())) {
                resultMap = getGroupMsg(uamGroupDtoResult,"xebest");
                //大区身份
            } else {
                resultMap = getGroupMsg(uamGroupDtoResult,"area");
            }
            //基地身份
        } else if (StringUtils.equals(groupType,"3")) {
            resultMap = getGroupMsg(uamGroupDtoResult,"base");
            //仓库身份, 其他身份怎么处理?
        } else {
            resultMap = null;
        }
        if (null == resultMap) {
            throw new BusinessException("您所登录的用户大区基地信息不完整!");
        }
        return resultMap;
    }



    /**
     * 根据登录用户获取大区, 基地
     * @param uamGroupDto 组织实体对象
     * @param identity 身份标志位
     * @return
     */
    private Map<String, List<OfcGroupVo>> getGroupMsg(UamGroupDto uamGroupDto, String identity) {
        if (null == uamGroupDto || PubUtils.isSEmptyOrNull(identity)) {
            throw new BusinessException("根据登录用户获取组织信息入参有误!");
        }
        Map<String, List<OfcGroupVo>> resultMap = new HashMap<>(1024);
        List<OfcGroupVo> areaList = new ArrayList<>();
        List<OfcGroupVo> baseList = new ArrayList<>();
        String userSerialNo = uamGroupDto.getSerialNo();
        //鲜易供应链
        if (StringUtils.equals("xebest",identity)) {
            //鲜易供应链下所有大区
            if (PubUtils.isSEmptyOrNull(uamGroupDto.getSerialNo())) {
                throw new BusinessException("根据登录用户获取大区列表入参有误!");
            }
            Wrapper<List<UamGroupDto>> childGroupInfoByParentSerilNoArea = uamGroupEdasService.getChildGroupInfoByParentSerilNo(userSerialNo);
            checkUamGroupEdasResultNullOrError(childGroupInfoByParentSerilNoArea);
            List<UamGroupDto> uamGroupDtoListArea = childGroupInfoByParentSerilNoArea.getResult();
            if (CollectionUtils.isEmpty(uamGroupDtoListArea)) {
                throw new BusinessException("查询当前登录用户组织信息出错:查询到的结果有误,或当前组织下没有子组织");
            }

            for(UamGroupDto uamGroupDtoAreaResult : uamGroupDtoListArea) {
                if (null != uamGroupDtoAreaResult
                        && !PubUtils.isSEmptyOrNull(uamGroupDtoAreaResult.getSerialNo())
                        && !PubUtils.isSEmptyOrNull(uamGroupDtoAreaResult.getGroupName())) {
                    OfcGroupVo ofcGroupVoArea = new OfcGroupVo();
                    ofcGroupVoArea.setSerialNo(uamGroupDtoAreaResult.getSerialNo());
                    ofcGroupVoArea.setGroupName(uamGroupDtoAreaResult.getGroupName());
                    areaList.add(ofcGroupVoArea);
                    //获取当前大区下的所有基地
                    baseList.addAll(getBaseListByCurArea(uamGroupDtoAreaResult));
                }
            }
            OfcGroupVo defaultBase = new OfcGroupVo();
            defaultBase.setSerialNo("");
            defaultBase.setGroupName("");
            baseList.add(0,defaultBase);
            OfcGroupVo defaultArea = new OfcGroupVo();
            defaultArea.setSerialNo("");
            defaultArea.setGroupName("");
            areaList.add(0,defaultArea);
            //大区
        } else if (StringUtils.equals("area",identity)) {
            OfcGroupVo ofcGroupVo = new OfcGroupVo();
            ofcGroupVo.setSerialNo(uamGroupDto.getSerialNo());
            ofcGroupVo.setGroupName(uamGroupDto.getGroupName());
            areaList.add(ofcGroupVo);
            //获取当前大区下的所有基地
            baseList = getBaseListByCurArea(uamGroupDto);
            OfcGroupVo defaultBase = new OfcGroupVo();
            defaultBase.setSerialNo("");
            defaultBase.setGroupName("");
            baseList.add(0,defaultBase);
            //基地
        } else if (StringUtils.equals("base",identity)) {
            if (PubUtils.isSEmptyOrNull(uamGroupDto.getSerialNo())
                    || PubUtils.isSEmptyOrNull(uamGroupDto.getGroupName())) {
                throw new BusinessException("当前基地"+ uamGroupDto.getGroupName() +"信息不完整!");
            }
            //根据当前基地获取父级大区
            OfcGroupVo ofcGroupVoArea = queryAreaMsgByBase(uamGroupDto);
            areaList.add(ofcGroupVoArea);
            OfcGroupVo ofcGroupVoBase = new OfcGroupVo();
            ofcGroupVoBase.setSerialNo(uamGroupDto.getSerialNo());
            ofcGroupVoBase.setGroupName(uamGroupDto.getGroupName());
            baseList.add(ofcGroupVoBase);
        }
        if (areaList.size() < 1 || baseList.size() < 1) {
            return null;
        }
        resultMap.put("area",areaList);
        resultMap.put("base",baseList);
        return resultMap;
    }

    /**
     * 获取当前大区下的所有基地
     * @param uamGroupDto 组织实体对象
     */
    @Override
    public List<OfcGroupVo> getBaseListByCurArea(UamGroupDto uamGroupDto) {
        if (null == uamGroupDto || PubUtils.isSEmptyOrNull(uamGroupDto.getSerialNo())) {
            throw new BusinessException("获取当前大区下的所有基地失败");
        }
        Wrapper<List<UamGroupDto>> childGroupInfoByParentSerilNoBase = uamGroupEdasService.getChildGroupInfoByParentSerilNo(uamGroupDto.getSerialNo());
        checkUamGroupEdasResultNullOrError(childGroupInfoByParentSerilNoBase);
        List<UamGroupDto> uamGroupDtoListBase = childGroupInfoByParentSerilNoBase.getResult();
        if (CollectionUtils.isEmpty(uamGroupDtoListBase)) {
            throw new BusinessException("查询当前登录用户组织信息出错:查询到的结果有误,或当前组织下没有子组织");
        }
        List<OfcGroupVo> baseList = new ArrayList<>();
        for(UamGroupDto uamGroupDtoBaseResult : uamGroupDtoListBase) {
            if (null != uamGroupDtoBaseResult
                    && !PubUtils.isSEmptyOrNull(uamGroupDtoBaseResult.getSerialNo())
                    && !PubUtils.isSEmptyOrNull(uamGroupDtoBaseResult.getGroupName())) {
                OfcGroupVo ofcGroupVo = new OfcGroupVo();
                ofcGroupVo.setSerialNo(uamGroupDtoBaseResult.getSerialNo());
                ofcGroupVo.setGroupName(uamGroupDtoBaseResult.getGroupName());
                baseList.add(ofcGroupVo);
            }
        }
        return baseList;
    }

    /**
     * 根据所选基地反查大区
     * @param uamGroupDto 组织实体对象
     * @return 组织实体对象
     */
    @Override
    public OfcGroupVo queryAreaMsgByBase(UamGroupDto uamGroupDto) {
        if (null == uamGroupDto || PubUtils.isSEmptyOrNull(uamGroupDto.getSerialNo())) {
            throw new BusinessException("所选地址未配置大区基地信息");
        }
        Wrapper<UamGroupDto> parentInfoByChildSerilNo = uamGroupEdasService.getParentInfoByChildSerilNo(uamGroupDto.getSerialNo());
        checkUamGroupEdasResultNullOrError(parentInfoByChildSerilNo);
        UamGroupDto uamGroupDtoResult = parentInfoByChildSerilNo.getResult();
        if (null == uamGroupDtoResult) {
            throw new BusinessException("当前基地"+ uamGroupDto.getGroupName() +"没有所属大区!");
        }
        if (PubUtils.isSEmptyOrNull(uamGroupDtoResult.getSerialNo())
                || PubUtils.isSEmptyOrNull(uamGroupDtoResult.getGroupName())) {
            throw new BusinessException("当前基地"+ uamGroupDto.getGroupName() +"所属大区信息不完整!");
        }
        OfcGroupVo ofcGroupVoArea = new OfcGroupVo();
        ofcGroupVoArea.setSerialNo(uamGroupDtoResult.getSerialNo());
        ofcGroupVoArea.setGroupName(uamGroupDtoResult.getGroupName());
        return ofcGroupVoArea;
    }

    /**
     * 校验UamGroupEdas返回结果
     * @param allGroupByType 查询当前登录用户组织信息UamGroupEdas返回结果
     */
    @Override
    public void checkUamGroupEdasResultNullOrError(Wrapper<?> allGroupByType) {
        if (null == allGroupByType) {
            throw new BusinessException("查询当前登录用户组织信息出错,接口返回null");
        }
        if (Wrapper.ERROR_CODE == allGroupByType.getCode()) {
            throw new BusinessException("查询当前登录用户组织信息出错:{}",allGroupByType.getMessage());
        }
    }

    @Override
    public Map<String, List<OfcGroupVo>> loadGroupList() {
        logger.info("查询所有组织信息");
        UamGroupDto uamGroupDto = new UamGroupDto();
        /**鲜易供应链身份**/
        uamGroupDto.setSerialNo("GD1625000003");
        Map<String, List<OfcGroupVo>> xebest = this.getGroupMsg(uamGroupDto, "xebest");
        return xebest;
    }

    @Override
    public OfcOrderInfoDTO queryOrderDetailByOrderCode(String orderCode) {
        OfcOrderInfoDTO orderInfoDTO = new OfcOrderInfoDTO();
        try {
            //订单基本信息
            OfcFundamentalInformation ofcFundamentalInformation = new OfcFundamentalInformation();
            ofcFundamentalInformation.setOrderCode(orderCode);
            ofcFundamentalInformation = ofcFundamentalInformationService.selectOne(ofcFundamentalInformation);
            //订单配送基本信息
            OfcDistributionBasicInfo ofcDistributionBasicInfo = new OfcDistributionBasicInfo();
            ofcDistributionBasicInfo.setOrderCode(orderCode);
            ofcDistributionBasicInfo = ofcDistributionBasicInfoService.selectOne(ofcDistributionBasicInfo);
            //财务信息
            OfcFinanceInformation ofcFinanceInformation = ofcFinanceInformationService.queryByOrderCode(orderCode);
            //仓储信息
            OfcWarehouseInformation ofcWarehouseInformation = ofcWarehouseInformationService.queryByOrderCode(orderCode);
            //订单状态集合
            List<OfcOrderStatus> ofcOrderStatusList = orderFollowOperService.queryOrderStatus(orderCode, "orderCode");
            //最新订单状态
            OfcOrderNewstatus orderNewstatus = new OfcOrderNewstatus();
            orderNewstatus.setOrderCode(orderCode);
            orderNewstatus = ofcOrderNewstatusService.selectOne(orderNewstatus);
            //货品信息
            OfcGoodsDetailsInfo ofcGoodsDetailsInfo = new OfcGoodsDetailsInfo();
            ofcGoodsDetailsInfo.setOrderCode(orderCode);
            List<OfcGoodsDetailsInfo> ofcGoodsDetailsInfoList = ofcGoodsDetailsInfoService.select(ofcGoodsDetailsInfo);
//            //运输车辆信息
            List<DeliverDetailsOrdeDto> deliverDetailsOrdeDtos = getDeliverDetails(orderCode);
            if (!CollectionUtils.isEmpty(deliverDetailsOrdeDtos)) {
                orderInfoDTO.setDeliverDetailsOrdeDtoList(deliverDetailsOrdeDtos);
            }
            orderInfoDTO.setOfcFundamentalInformation(ofcFundamentalInformation);
            orderInfoDTO.setOfcDistributionBasicInfo(ofcDistributionBasicInfo);
            orderInfoDTO.setOfcFinanceInformation(ofcFinanceInformation);
            orderInfoDTO.setOfcWarehouseInformation(ofcWarehouseInformation);
            orderInfoDTO.setCurrentStatus(orderNewstatus);
            orderInfoDTO.setOrderStatusList(ofcOrderStatusList);
            orderInfoDTO.setGoodsDetailsInfoList(ofcGoodsDetailsInfoList);
        } catch (Exception ex) {
            logger.error("查询订单明细信息发生异常：异常详情{}", ex);
            throw ex;
        }
        return orderInfoDTO;
    }

    @Override
    public OfcOrderInfoDTO queryOrderMainDetailByOrderCode(String orderCode) {
        //订单基本信息
        OfcFundamentalInformation ofcFundamentalInformation = new OfcFundamentalInformation();
        ofcFundamentalInformation.setOrderCode(orderCode);
        ofcFundamentalInformation = ofcFundamentalInformationService.selectByKey(ofcFundamentalInformation);
        //订单配送基本信息
        OfcDistributionBasicInfo ofcDistributionBasicInfo = ofcDistributionBasicInfoService.queryByOrderCode(orderCode);
        //订单仓储基本信息
        OfcWarehouseInformation ofcWarehouseInformation = ofcWarehouseInformationService.queryByOrderCode(orderCode);
        OfcOrderInfoDTO ofcOrderInfoDTO = new OfcOrderInfoDTO();
        ofcOrderInfoDTO.setOfcFundamentalInformation(ofcFundamentalInformation);
        ofcOrderInfoDTO.setOfcDistributionBasicInfo(ofcDistributionBasicInfo);
        ofcOrderInfoDTO.setOfcWarehouseInformation(ofcWarehouseInformation);
        return ofcOrderInfoDTO;
    }

    private List<DeliverDetailsOrdeDto> getDeliverDetails(String orderCode){
        try {
            Wrapper<List<DeliverDetailsOrdeDto>> result = acGetDeliveryOrderEdasService.queryTransportDetail(orderCode);
            if (result.getCode() == Wrapper.SUCCESS_CODE) {
                return result.getResult();
            }
        }catch (Exception e) {
            logger.error("查询订单运输车辆信息发生异常：异常详情{}", e);
            return null;
        }
        return null;
    }
}

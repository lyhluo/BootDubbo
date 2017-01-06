package com.xescm.ofc.service.impl;

import com.xescm.base.model.dto.auth.AuthResDto;
import com.xescm.base.model.wrap.WrapMapper;
import com.xescm.base.model.wrap.Wrapper;
import com.xescm.core.utils.PubUtils;
import com.xescm.csc.model.dto.CscSupplierInfoDto;
import com.xescm.csc.model.dto.QueryCustomerCodeDto;
import com.xescm.csc.model.dto.contantAndCompany.CscContantAndCompanyDto;
import com.xescm.csc.model.vo.CscCustomerVo;
import com.xescm.csc.provider.CscCustomerEdasService;
import com.xescm.ofc.constant.OrderConstConstant;
import com.xescm.ofc.domain.*;
import com.xescm.ofc.enums.ResultCodeEnum;
import com.xescm.ofc.exception.BusinessException;
import com.xescm.ofc.model.dto.ofc.OfcOrderDTO;
import com.xescm.ofc.service.*;
import com.xescm.ofc.utils.CodeGenUtils;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.xescm.ofc.constant.OrderConstConstant.*;

/**
 * 订单下单相关处理Service
 * Created by ydx on 2016/10/12.
 */
@Service
@Transactional//既能注解在方法上,也能注解在类上.当注解在类上的时候,意味着这个类的所有public方法都是开启事务的,如果类级别和方法级别同事使用了该注解,则方法覆盖类.
//@Transactional(rollbackFor={xxx.class})
public class OfcOrderPlaceServiceImpl implements OfcOrderPlaceService {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    private OfcOrderStatusService ofcOrderStatusService;
    @Resource
    private OfcGoodsDetailsInfoService ofcGoodsDetailsInfoService;
    @Resource
    private OfcFundamentalInformationService ofcFundamentalInformationService;
    @Resource
    private OfcFinanceInformationService ofcFinanceInformationService;
    @Resource
    private OfcDistributionBasicInfoService ofcDistributionBasicInfoService;
    @Resource
    private OfcWarehouseInformationService ofcWarehouseInformationService;
    @Resource
    private OfcOrderManageService ofcOrderManageService;
    @Resource
    private OfcMerchandiserService ofcMerchandiserService;
    @Resource
    private CodeGenUtils codeGenUtils;
    @Resource
    private CscCustomerEdasService cscCustomerEdasService;

    private ModelMapper modelMapper = new ModelMapper();


    /**
     * 根据四级判断设置城配或者干线处理
     * @param ofcDistributionBasicInfo 运输信息
     * @param ofcFundamentalInformation 基本信息
     */
    private void setCityOrTrunk(OfcDistributionBasicInfo ofcDistributionBasicInfo,OfcFundamentalInformation ofcFundamentalInformation){
        if(!PubUtils.isSEmptyOrNull(ofcDistributionBasicInfo.getDeparturePlaceCode()) && ofcDistributionBasicInfo.getDeparturePlaceCode().length() > 12){
            String depatrueCode = ofcDistributionBasicInfo.getDeparturePlaceCode().substring(0,13);
            String destinationCode = ofcDistributionBasicInfo.getDestinationCode().substring(0,13);
            if(depatrueCode.equals(destinationCode)){
                ofcFundamentalInformation.setBusinessType(WITHTHECITY);
            }else {
                ofcFundamentalInformation.setBusinessType(WITHTHETRUNK);
            }
        }else{
            throw new BusinessException("四级地址编码为空!");
        }
    }

    /**
     * 保存货品详情
     * @param ofcGoodsDetailsInfos 订单货品明细信息
     * @param ofcFundamentalInformation 基本信息
     */
    private BigDecimal saveDetails(List<OfcGoodsDetailsInfo> ofcGoodsDetailsInfos,OfcFundamentalInformation ofcFundamentalInformation){
        BigDecimal goodsAmountCount = new BigDecimal(0);
        for(OfcGoodsDetailsInfo ofcGoodsDetails : ofcGoodsDetailsInfos){
            if(ofcGoodsDetails.getQuantity() == null || ofcGoodsDetails.getQuantity().compareTo(new BigDecimal(0)) == 0 ){
                if((ofcGoodsDetails.getWeight() != null && ofcGoodsDetails.getWeight().compareTo(new BigDecimal(0)) != 0 ) || (ofcGoodsDetails.getCubage() != null && ofcGoodsDetails.getCubage().compareTo(new BigDecimal(0)) != 0 )){
                }else{
                    continue;
                }
            }
            String orderCode = ofcFundamentalInformation.getOrderCode();
            ofcGoodsDetails.setGoodsCode(ofcGoodsDetails.getGoodsCode().split("\\@")[0]);
            ofcGoodsDetails.setOrderCode(orderCode);
            ofcGoodsDetails.setCreationTime(ofcFundamentalInformation.getCreationTime());
            ofcGoodsDetails.setCreator(ofcFundamentalInformation.getCreator());
            ofcGoodsDetails.setOperator(ofcFundamentalInformation.getOperator());
            ofcGoodsDetails.setOperTime(ofcFundamentalInformation.getOperTime());
            goodsAmountCount = goodsAmountCount.add(ofcGoodsDetails.getQuantity(), new MathContext(3));
            ofcGoodsDetailsInfoService.save(ofcGoodsDetails);
        }
        return goodsAmountCount;
    }

    @Override
    public String placeOrder(OfcOrderDTO ofcOrderDTO, List<OfcGoodsDetailsInfo> ofcGoodsDetailsInfos, String tag, AuthResDto authResDtoByToken, String custId
                            , CscContantAndCompanyDto cscContantAndCompanyDtoConsignor
                            , CscContantAndCompanyDto cscContantAndCompanyDtoConsignee, CscSupplierInfoDto cscSupplierInfoDto) {
        Wrapper<?> wrapperFun = validateFundamentalMessage(ofcOrderDTO);
        if(Wrapper.ERROR_CODE == wrapperFun.getCode()){
            throw new BusinessException(wrapperFun.getMessage());
        }
        OfcFinanceInformation  ofcFinanceInformation =modelMapper.map(ofcOrderDTO, OfcFinanceInformation.class);
        OfcFundamentalInformation ofcFundamentalInformation = modelMapper.map(ofcOrderDTO, OfcFundamentalInformation.class);
        OfcDistributionBasicInfo ofcDistributionBasicInfo = modelMapper.map(ofcOrderDTO, OfcDistributionBasicInfo.class);
        OfcWarehouseInformation  ofcWarehouseInformation = modelMapper.map(ofcOrderDTO, OfcWarehouseInformation.class);
        OfcMerchandiser ofcMerchandiser=modelMapper.map(ofcOrderDTO,OfcMerchandiser.class);
        ofcFundamentalInformation.setCreationTime(new Date());
        ofcFundamentalInformation.setCreator(authResDtoByToken.getUserId());
        ofcFundamentalInformation.setCreatorName(authResDtoByToken.getUserName());
        ofcFundamentalInformation.setOperator(authResDtoByToken.getUserId());
        ofcFundamentalInformation.setOperatorName(authResDtoByToken.getUserName());
        ofcFundamentalInformation.setOperTime(new Date());
        OfcOrderStatus ofcOrderStatus=new OfcOrderStatus();
        //ofcFundamentalInformation.setStoreCode(ofcOrderDTO.getStoreName());//店铺还没维护表
        ofcFundamentalInformation.setStoreName(ofcOrderDTO.getStoreName());//店铺还没维护表
        ofcFundamentalInformation.setOrderSource("手动");//订单来源
            if (PubUtils.trimAndNullAsEmpty(tag).equals("place")){//下单
                StringBuffer notes = new StringBuffer();
                int custOrderCode = 0;
                if(!PubUtils.isSEmptyOrNull(ofcFundamentalInformation.getCustOrderCode())){
                    custOrderCode = ofcFundamentalInformationService.checkCustOrderCode(ofcFundamentalInformation);
                }

                if (custOrderCode < 1){//根据客户订单编号查询唯一性
                    ofcFundamentalInformation.setOrderCode(codeGenUtils.getNewWaterCode("SO",6));
                    //"SO"+ PrimaryGenerater.getInstance()
                    //        .generaterNextNumber(PrimaryGenerater.getInstance().getLastNumber())
                    ofcFundamentalInformation.setCustCode(custId);
                    if(PubUtils.isSEmptyOrNull(ofcFundamentalInformation.getCustName())){
                        QueryCustomerCodeDto queryCustomerCodeDto = new QueryCustomerCodeDto();
                        queryCustomerCodeDto.setCustomerCode(custId);
                        Wrapper<CscCustomerVo> cscCustomerVo = (Wrapper<CscCustomerVo>) cscCustomerEdasService.queryCustomerByCustomerCodeOrId(queryCustomerCodeDto);
                        if(Wrapper.ERROR_CODE == cscCustomerVo.getCode()){
                            throw new BusinessException(cscCustomerVo.getMessage());
                        }else if(null == cscCustomerVo.getResult()){
                            throw new BusinessException("客户中心没有查到该客户!");
                        }
                        ofcFundamentalInformation.setCustName(cscCustomerVo.getResult().getCustomerName());
                    }
                    ofcFundamentalInformation.setAbolishMark(ORDERWASNOTABOLISHED);//未作废
                    //添加该订单的货品信息 modify by wangst 做抽象处理
                    BigDecimal goodsAmountCount = saveDetails(ofcGoodsDetailsInfos,ofcFundamentalInformation);
                    ofcDistributionBasicInfo.setQuantity(goodsAmountCount);
                    if (ofcFundamentalInformation.getOrderType().equals(WAREHOUSEDISTRIBUTIONORDER)){

                        if(null == ofcWarehouseInformation.getProvideTransport()){
                            ofcWarehouseInformation.setProvideTransport(WAREHOUSEORDERNOTPROVIDETRANS);
                        }
                        if(ofcWarehouseInformation.getProvideTransport()== WAREHOUSEORDERPROVIDETRANS){
                            Wrapper<?> wrapper = validateDistrictContactMessage(cscContantAndCompanyDtoConsignor, cscContantAndCompanyDtoConsignee);
                            if(Wrapper.ERROR_CODE == wrapper.getCode()){
                                throw new BusinessException(wrapper.getMessage());
                            }
                            //校验运输基本信息
                            checkDistibutionBaseMsg(ofcDistributionBasicInfo);


                            addDistributionInfo(ofcDistributionBasicInfo, ofcFundamentalInformation);
                            /*saveContactMessage(cscContantAndCompanyDtoConsignor,custId,authResDtoByToken);
                            saveContactMessage(cscContantAndCompanyDtoConsignee,custId,authResDtoByToken);*/
                        }
                        // 更新仓配信息
                        upOfcWarehouseInformation(ofcWarehouseInformation,ofcFundamentalInformation);
                        String businessTypeHead = ofcFundamentalInformation.getBusinessType().substring(0,2);
                        if("62".equals(businessTypeHead)){//如果是入库才有供应商信息//这儿才是入库
                            /*Wrapper<?> wrapper = validateSupportContactMessage(cscSupplierInfoDto);
                            if(Wrapper.ERROR_CODE == wrapper.getCode()){
                                throw new BusinessException(wrapper.getMessage());
                            }*/
                           // saveSupportMessage(cscSupplierInfoDto,custId,authResDtoByToken);

                        }
                        ofcWarehouseInformationService.save(ofcWarehouseInformation);
                        if("61".equals(businessTypeHead)){//如果是入库才有供应商信息//这儿是出库
                            ofcWarehouseInformation.setSupportCode("");
                            ofcWarehouseInformation.setSupportName("");
                        }
                    }else if(ofcFundamentalInformation.getOrderType().equals(TRANSPORTORDER)){
                        Wrapper<?> wrapper = validateDistrictContactMessage(cscContantAndCompanyDtoConsignor, cscContantAndCompanyDtoConsignee);
                        if(Wrapper.ERROR_CODE == wrapper.getCode()){
                            throw new BusinessException(wrapper.getMessage());
                        }
                        //校验运输基本信息
                        checkDistibutionBaseMsg(ofcDistributionBasicInfo);
                        //运输订单
                        //设置城配或者干线 add by wangst
                        setCityOrTrunk(ofcDistributionBasicInfo,ofcFundamentalInformation);
                       // ofcFundamentalInformation.setBusinessType(OrderConstEnum.WITHTHEKABAN);
                        //保存运输信息
                        addDistributionInfo(ofcDistributionBasicInfo, ofcFundamentalInformation);
                        /*saveContactMessage(cscContantAndCompanyDtoConsignor,custId,authResDtoByToken);
                        saveContactMessage(cscContantAndCompanyDtoConsignee,custId,authResDtoByToken);*/
                    }else{
                        throw new BusinessException("您选择的订单类型系统无法识别!");
                    }

                    notes.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                    notes.append(" 订单已创建");
                    notes.append(" 操作人: ").append(authResDtoByToken.getUserName());
                    notes.append(" 操作单位: ").append(authResDtoByToken.getGroupRefName());
                    ofcOrderStatus.setNotes(notes.toString());
                    upOrderStatus(ofcOrderStatus,ofcFundamentalInformation,authResDtoByToken);

                    //添加基本信息
                    ofcFundamentalInformationService.save(ofcFundamentalInformation);
                    if(ofcMerchandiserService.select(ofcMerchandiser).size()==0 && !PubUtils.trimAndNullAsEmpty(ofcMerchandiser.getMerchandiser()).equals("")){
                        ofcMerchandiserService.save(ofcMerchandiser);
                    }
                    if(!PubUtils.isSEmptyOrNull(ofcFundamentalInformation.getOrderBatchNumber())){
                        //进行自动审核
                        ofcOrderManageService.orderAutoAuditFromOperation(ofcFundamentalInformation,ofcGoodsDetailsInfos,ofcDistributionBasicInfo,
                                ofcWarehouseInformation,ofcFinanceInformation,ofcOrderStatus.getOrderStatus(),"review",authResDtoByToken);
                    }

                }else{
                    throw new BusinessException("该客户订单编号已经存在!您不能重复下单!");
                }
            }else if (PubUtils.trimAndNullAsEmpty(tag).equals("manage")){ //编辑

                //现在订单编辑没有对客户订单编号进行校验, 客户订单编号可以不写!
                /*if(PubUtils.isSEmptyOrNull(ofcFundamentalInformation.getCustOrderCode())){
                    throw new BusinessException("您没有填写客户订单编号!");
                }*/
                /*if (("").equals(PubUtils.trimAndNullAsEmpty(ofcFundamentalInformation.getOrderCode())) || null == PubUtils.trimAndNullAsEmpty(ofcFundamentalInformation.getOrderCode())){
                    ofcFundamentalInformation.setOrderCode(ofcFundamentalInformationService.selectOne(ofcFundamentalInformation).getOrderCode());
                }*/
                //仓配订单
                //删除之前订单的货品信息
                OfcGoodsDetailsInfo ofcGoodsDetailsInfo = new OfcGoodsDetailsInfo();
                ofcGoodsDetailsInfo.setOrderCode(ofcOrderDTO.getOrderCode());
                ofcGoodsDetailsInfoService.delete(ofcGoodsDetailsInfo);
                //添加该订单的货品信息 modify by wangst 做抽象处理
                BigDecimal goodsAmountCount = saveDetails(ofcGoodsDetailsInfos,ofcFundamentalInformation);
                ofcDistributionBasicInfo.setQuantity(goodsAmountCount);
                if (ofcFundamentalInformation.getOrderType().equals(WAREHOUSEDISTRIBUTIONORDER)){//编辑时仓配订单
                    if(null == ofcWarehouseInformation.getProvideTransport()){
                        ofcWarehouseInformation.setProvideTransport(WAREHOUSEORDERNOTPROVIDETRANS);
                    }
                    //仓配单需要运输
                    if(ofcWarehouseInformation.getProvideTransport()== WAREHOUSEORDERPROVIDETRANS){
                        Wrapper<?> wrapper = validateDistrictContactMessage(cscContantAndCompanyDtoConsignor, cscContantAndCompanyDtoConsignee);
                        if(Wrapper.ERROR_CODE == wrapper.getCode()){
                            throw new BusinessException(wrapper.getMessage());
                        }
                        //校验运输基本信息
                        checkDistibutionBaseMsg(ofcDistributionBasicInfo);
                        //如果编辑订单后, 还是需要提供运输, 就要更新运输信息
                        upDistributionBasicInfo(ofcDistributionBasicInfo,ofcFundamentalInformation);
                        /*saveContactMessage(cscContantAndCompanyDtoConsignor,custId,authResDtoByToken);
                        saveContactMessage(cscContantAndCompanyDtoConsignee,custId,authResDtoByToken);*/
                        OfcDistributionBasicInfo ofcDist = new OfcDistributionBasicInfo();
                        ofcDist.setOrderCode(ofcFundamentalInformation.getOrderCode());
                        List<OfcDistributionBasicInfo> select = ofcDistributionBasicInfoService.select(ofcDist);
                        if(select.size() > 0){//有运输信息
                            ofcDistributionBasicInfoService.updateByOrderCode(ofcDistributionBasicInfo);
                        }else if (select.size() == 0){
                            addDistributionInfo(ofcDistributionBasicInfo, ofcFundamentalInformation);
                        }
                    //仓配单不需要运输,需要将属于该订单的运输信息删除
                    }else if (ofcWarehouseInformation.getProvideTransport() == WAREHOUSEORDERNOTPROVIDETRANS){
                        ofcFundamentalInformation.setSecCustCode("");
                        ofcFundamentalInformation.setSecCustName("");
                        int result = ofcDistributionBasicInfoService.deleteByOrderCode(ofcFundamentalInformation.getOrderCode());

                    }
                    // 更新仓配信息
                    upOfcWarehouseInformation(ofcWarehouseInformation,ofcFundamentalInformation);
                    //入库
                    if("62".equals(ofcFundamentalInformation.getBusinessType().substring(0,2))){//如果是入库才有供应商信息
                       /* Wrapper<?> wrapper = validateSupportContactMessage(cscSupplierInfoDto);
                        if(Wrapper.ERROR_CODE == wrapper.getCode()){
                            throw new BusinessException(wrapper.getMessage());
                        }*/
                        //String saveSupportMessageResult = saveSupportMessage(cscSupplierInfoDto,custId,authResDtoByToken);
                    }
                    //出库
                    if("61".equals(ofcFundamentalInformation.getBusinessType().substring(0,2))){//如果是入库才有供应商信息
                        ofcWarehouseInformation.setSupportCode("");
                        ofcWarehouseInformation.setSupportName("");
                        //去数据库清掉相应的供应商信息
                    }
                    //先去数据库查, 如果有就是改, 如果没有就是增
                    OfcWarehouseInformation ofcWare = new OfcWarehouseInformation();
                    ofcWare.setOrderCode(ofcFundamentalInformation.getOrderCode());
                    List<OfcWarehouseInformation> select = ofcWarehouseInformationService.select(ofcWare);
                    if(select.size() > 0){//有
                        ofcWarehouseInformationService.updateByOrderCode(ofcWarehouseInformation);
                    }else if(select.size() == 0){
                        saveWarehouseMessage(ofcWarehouseInformation);
                    }
                }else if(ofcFundamentalInformation.getOrderType().equals(TRANSPORTORDER)){
                    Wrapper<?> wrapper = validateDistrictContactMessage(cscContantAndCompanyDtoConsignor, cscContantAndCompanyDtoConsignee);
                    if(Wrapper.ERROR_CODE == wrapper.getCode()){
                        throw new BusinessException(wrapper.getMessage());
                    }
                    //校验运输基本信息
                    checkDistibutionBaseMsg(ofcDistributionBasicInfo);
                    //删除仓配信息
                    OfcWarehouseInformation ofcWarehouseInformationForTrans = new OfcWarehouseInformation();
                    ofcWarehouseInformationForTrans.setOrderCode(ofcFundamentalInformation.getOrderCode());
                    ofcWarehouseInformationService.delete(ofcWarehouseInformationForTrans);
                    /*saveContactMessage(cscContantAndCompanyDtoConsignor,custId,authResDtoByToken);
                    saveContactMessage(cscContantAndCompanyDtoConsignee,custId,authResDtoByToken);*/
                    OfcDistributionBasicInfo ofcDist = new OfcDistributionBasicInfo();
                    ofcDist.setOrderCode(ofcFundamentalInformation.getOrderCode());
                    List<OfcDistributionBasicInfo> select = ofcDistributionBasicInfoService.select(ofcDist);
                    //设置城配或者干线 add by wangst
                    setCityOrTrunk(ofcDistributionBasicInfo,ofcFundamentalInformation);
                    if(select.size() > 0){//有运输信息
                        ofcDistributionBasicInfoService.updateByOrderCode(ofcDistributionBasicInfo);
                    }else if (select.size() < 0){
                        addDistributionInfo(ofcDistributionBasicInfo, ofcFundamentalInformation);
                    }
                }else{
                    throw new BusinessException("您的订单类型系统无法识别!");
                }

                ofcFundamentalInformation.setOperator(authResDtoByToken.getUserId());
                ofcFundamentalInformation.setOperatorName(authResDtoByToken.getUserName());
                ofcFundamentalInformation.setOperTime(new Date());
                ofcOrderStatus.setNotes(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                        +" "+"订单已更新");
                upOrderStatus(ofcOrderStatus,ofcFundamentalInformation,authResDtoByToken);
                ofcFundamentalInformationService.update(ofcFundamentalInformation);
            }else if(PubUtils.trimAndNullAsEmpty(tag).equals("tranplace")){
                StringBuffer notes = new StringBuffer();
                // 校验当前客户的客户订单号是否重复
                String custOrderCode = ofcFundamentalInformation.getCustOrderCode();
                String custCode = ofcFundamentalInformation.getCustCode();
                if (!PubUtils.isSEmptyOrNull(custOrderCode) && !PubUtils.isSEmptyOrNull(custCode)) {
                    boolean isDup = checkOrderCode(custOrderCode, custCode);
                    if (isDup) {
                        throw new BusinessException("当前客户存在重复客户订单号！");
                    }
                } else {
                    if (PubUtils.isSEmptyOrNull(custCode)) {
                        throw new BusinessException("客户不能为空！");
                    }
                }

                if(!PubUtils.trimAndNullAsEmpty(ofcDistributionBasicInfo.getTransCode()).equals("")){
                    int orderCodeByTransCode = ofcDistributionBasicInfoService.checkTransCode(ofcDistributionBasicInfo);
                    if(orderCodeByTransCode>=1){
                        throw new BusinessException("该运输单号号已经存在!您不能重复下单!");
                    }
                }
                ofcFundamentalInformation.setOrderCode(codeGenUtils.getNewWaterCode("SO",6));
               // ofcFundamentalInformation.setCustName(authResDtoByToken.getGroupRefName());
                ofcFundamentalInformation.setAbolishMark(ORDERWASNOTABOLISHED);//未作废
                ofcFundamentalInformation.setOrderType(TRANSPORTORDER);
                if(ofcFundamentalInformation.getOrderType().equals(TRANSPORTORDER)){
                    Wrapper<?> wrapper = validateDistrictContactMessage(cscContantAndCompanyDtoConsignor, cscContantAndCompanyDtoConsignee);
                    if(Wrapper.ERROR_CODE == wrapper.getCode()){
                        throw new BusinessException(wrapper.getMessage());
                    }
                    //校验运输基本信息
                    checkDistibutionBaseMsg(ofcDistributionBasicInfo);

                    //运输订单
                    if(!PubUtils.isSEmptyOrNull(ofcDistributionBasicInfo.getDeparturePlaceCode()) && ofcDistributionBasicInfo.getDeparturePlaceCode().length() > 12){
                        String depatrueCode = ofcDistributionBasicInfo.getDeparturePlaceCode().substring(0,13);
                        String destinationCode = ofcDistributionBasicInfo.getDestinationCode().substring(0,13);
                        /*if(depatrueCode.equals(destinationCode)){
                            ofcFundamentalInformation.setBusinessType(OrderConstEnum.WITHTHECITY);
                        }else {
                            ofcFundamentalInformation.setBusinessType(OrderConstEnum.WITHTHETRUNK);
                        }*/
                    }else{
                        throw new BusinessException("四级地址编码为空!");
                    }
                    addFinanceInformation(ofcFinanceInformation,ofcFundamentalInformation);
                    addDistributionInfo(ofcDistributionBasicInfo, ofcFundamentalInformation);
                    /*saveContactMessage(cscContantAndCompanyDtoConsignor,custId,authResDtoByToken);
                    saveContactMessage(cscContantAndCompanyDtoConsignee,custId,authResDtoByToken);*/
                }else{
                    throw new BusinessException("您选择的订单类型系统无法识别!");
                }

                notes.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                notes.append(" 订单已创建");
                notes.append(" 操作人: ").append(authResDtoByToken.getUserName());
                notes.append(" 操作单位: ").append(authResDtoByToken.getGroupRefName());
                ofcOrderStatus.setNotes(notes.toString());
                upOrderStatus(ofcOrderStatus,ofcFundamentalInformation,authResDtoByToken);
                //添加该订单的货品信息
                List<OfcGoodsDetailsInfo> goodsDetailsList=new ArrayList<OfcGoodsDetailsInfo>();
                for(OfcGoodsDetailsInfo ofcGoodsDetails : ofcGoodsDetailsInfos){
                    String orderCode = ofcFundamentalInformation.getOrderCode();
                    ofcGoodsDetails.setOrderCode(orderCode);
                    ofcGoodsDetails.setCreationTime(ofcFundamentalInformation.getCreationTime());
                    ofcGoodsDetails.setCreator(ofcFundamentalInformation.getCreator());
                    ofcGoodsDetails.setOperator(ofcFundamentalInformation.getOperator());
                    ofcGoodsDetails.setOperTime(ofcFundamentalInformation.getOperTime());
                    ofcGoodsDetailsInfoService.save(ofcGoodsDetails);
                    goodsDetailsList.add(ofcGoodsDetails);
                }
                try {
                    //添加基本信息
                    ofcFundamentalInformationService.save(ofcFundamentalInformation);
                } catch (Exception ex) {
                    if (ex.getCause().getMessage().trim().startsWith("Duplicate entry")) {
                        logger.error("获取订单号发生重复，导致保存计划单基本信息发生错误！{}", ex);
                        throw new BusinessException("获取订单号发生重复，导致保存计划单基本信息发生错误！");
                    } else {
                        logger.error("保存计划单信息发生错误:{}", ex);
                        throw new BusinessException("保存计划单信息发生错误！", ex);
                    }
                }
                if(ofcMerchandiserService.select(ofcMerchandiser).size()==0){
                    ofcMerchandiserService.save(ofcMerchandiser);
                }
                ofcOrderManageService.orderAuditByTrans(ofcFundamentalInformation,goodsDetailsList,ofcDistributionBasicInfo,ofcFinanceInformation,ofcOrderStatus.getOrderStatus(),
                        "review",authResDtoByToken);
            } else if(PubUtils.trimAndNullAsEmpty(tag).equals("distributionPlace")){
                distributionOrderPlace(ofcFundamentalInformation,ofcGoodsDetailsInfos,ofcDistributionBasicInfo
                        ,ofcWarehouseInformation,ofcFinanceInformation,custId,cscContantAndCompanyDtoConsignor,cscContantAndCompanyDtoConsignee,authResDtoByToken
                        ,ofcOrderStatus,ofcMerchandiser);
            } else {
                throw new BusinessException("未知操作!系统无法识别!");
            }
        if("place".equals(tag) || "tranplace".equals(tag) || "distributionPlace".equals(tag)){
            return "您已成功下单!";
        }else if("manage".equals(tag)){
            return "您的订单修改成功!";
        }else {
            return ResultCodeEnum.ERROROPER.getName();
        }
    }

    /**
     * 根据客户编号和客户订单号校验重复
     * @param custOrderCode
     * @param custCode
     * @return
     */
    private boolean checkOrderCode (String custOrderCode, String custCode) {
        boolean isDup = false;
        // 校验客户订单号
        if(!PubUtils.isStrsEmptyOrNull(custOrderCode)) {
            OfcFundamentalInformation ofcFundamentalInfo = new OfcFundamentalInformation();
            ofcFundamentalInfo.setCustOrderCode(custOrderCode);
            ofcFundamentalInfo.setCustCode(custCode);
            int count = ofcFundamentalInformationService.checkCustOrderCode(ofcFundamentalInfo);
            if (count >= 1) {
                isDup = true;
            }
        }
        return isDup;
    }

    /**
     * 更新并保存订单状态
     * @param ofcOrderStatus 订单状态信息
     * @param ofcFundamentalInformation 基本信息
     * @param authResDtoByToken 登录的授权DTO
     */
    private void upOrderStatus(OfcOrderStatus ofcOrderStatus,OfcFundamentalInformation ofcFundamentalInformation,AuthResDto authResDtoByToken){
        ofcOrderStatus.setOrderCode(ofcFundamentalInformation.getOrderCode());
        ofcOrderStatus.setOrderStatus(PENDINGAUDIT);
        ofcOrderStatus.setStatusDesc("待审核");
        ofcOrderStatus.setLastedOperTime(new Date());
        ofcOrderStatus.setOperator(authResDtoByToken.getUserName());
        ofcOrderStatusService.save(ofcOrderStatus);
    }

    /**
     * 添加财务信息
     * @param ofcFinanceInformation 财务信息
     * @param ofcFundamentalInformation 基本信息
     */
    private void addFinanceInformation(OfcFinanceInformation ofcFinanceInformation, OfcFundamentalInformation ofcFundamentalInformation){
        ofcFinanceInformation=upFinanceInformation(ofcFinanceInformation,ofcFundamentalInformation);
        ofcFinanceInformationService.save(ofcFinanceInformation);
    }

    private OfcFinanceInformation upFinanceInformation(OfcFinanceInformation ofcFinanceInformation
            ,OfcFundamentalInformation ofcFundamentalInformation){
//        ofcDistributionBasicInfo.setTransCode(ofcFundamentalInformation.getOrderCode().replace("SO","TSO"));
        ofcFinanceInformation.setCreationTime(ofcFundamentalInformation.getCreationTime());
        ofcFinanceInformation.setCreator(ofcFundamentalInformation.getCreator());
        ofcFinanceInformation.setOrderCode(ofcFundamentalInformation.getOrderCode());
        ofcFinanceInformation.setOperator(ofcFundamentalInformation.getOperator());
        ofcFinanceInformation.setOperTime(ofcFundamentalInformation.getOperTime());
        return ofcFinanceInformation;
    }

    /**
     * 添加运输订单
     * @param ofcDistributionBasicInfo 运输信息
     * @param ofcFundamentalInformation 订单基本信息
     */
    private void addDistributionInfo(OfcDistributionBasicInfo ofcDistributionBasicInfo, OfcFundamentalInformation ofcFundamentalInformation){
        upDistributionBasicInfo(ofcDistributionBasicInfo,ofcFundamentalInformation);
        ofcDistributionBasicInfoService.save(ofcDistributionBasicInfo);
    }

    private void upDistributionBasicInfo(OfcDistributionBasicInfo ofcDistributionBasicInfo
            ,OfcFundamentalInformation ofcFundamentalInformation){
//        ofcDistributionBasicInfo.setTransCode(ofcFundamentalInformation.getOrderCode().replace("SO","TSO"));
//        ofcFundamentalInformation.setSecCustCode("001");
//        ofcFundamentalInformation.setSecCustName("众品");
        ofcDistributionBasicInfo.setCreationTime(ofcFundamentalInformation.getCreationTime());
        ofcDistributionBasicInfo.setCreator(ofcFundamentalInformation.getCreator());
        ofcDistributionBasicInfo.setOrderCode(ofcFundamentalInformation.getOrderCode());
        ofcDistributionBasicInfo.setOperator(ofcFundamentalInformation.getOperator());
        ofcDistributionBasicInfo.setOperTime(ofcFundamentalInformation.getOperTime());

        //如果订单类型是卡班订单, 则向DMS推送该订单
       /* if(OrderConstEnum.WITHTHEKABAN.equals(ofcFundamentalInformation.getBusinessType())){
            Wrapper<?> wrapper = feignOfcDistributionAPIClient.addDistributionBasicInfo(ofcDistributionBasicInfo);

            if(Wrapper.ERROR_CODE == wrapper.getCode()){
                throw new BusinessException("向分拣中心推送卡班订单失败");
            }
        }*/
    }

    private void upOfcWarehouseInformation(OfcWarehouseInformation ofcWarehouseInformation
            ,OfcFundamentalInformation ofcFundamentalInformation){
        /*ofcWarehouseInformation.setSupportCode("001");
        ofcWarehouseInformation.setSupportName("众品");*/
        ofcWarehouseInformation.setOrderCode(ofcFundamentalInformation.getOrderCode());
        ofcWarehouseInformation.setCreationTime(ofcFundamentalInformation.getCreationTime());
        ofcWarehouseInformation.setCreator(ofcFundamentalInformation.getCreator());
        ofcWarehouseInformation.setOperTime(ofcFundamentalInformation.getOperTime());
        ofcWarehouseInformation.setOperator(ofcFundamentalInformation.getOperator());
    }

    /**
     * 下单或编辑时在订单中心本地保存客户订单中的货品信息
     */
    public String saveOrderGoodsList(List<OfcGoodsDetailsInfo> ofcGoodsDetailsInfoList){
        if (ofcGoodsDetailsInfoList.size() < 1){
            return "未添加货品信息";
        }
        for (OfcGoodsDetailsInfo ofcGoodsDetailsInfo: ofcGoodsDetailsInfoList) {
            int save = ofcGoodsDetailsInfoService.save(ofcGoodsDetailsInfo);
            if(save == 0){
                throw new BusinessException("保存货品信息失败");
            }
        }
        return Wrapper.SUCCESS_MESSAGE;
    }
    /**
     * 下单或编辑时保存发货方收货方及其联系人
     * saveContactMessage(cscContantAndCompanyDtoConsignee,custId
     ,authResDtoByToken.getUserId(),authResDtoByToken.getGroupRefName(),authResDtoByToken.getGroupId());
     */
    /*public String saveContactMessage(CscContantAndCompanyDto cscContantAndCompanyDto,String custId,AuthResDto authResDtoByToken){//AuthResDto authResDtoByToken,//String custId,String userId,String userName,String groupId
        if(null == cscContantAndCompanyDto){
            throw new BusinessException("未添加联系人信息");
        }
        try {
            cscContantAndCompanyDto.setCustomerId(custId);
            Wrapper<List<CscContantAndCompanyVo>> listWrapper = feignCscCustomerAPIClient.queryCscReceivingInfoList(cscContantAndCompanyDto);
            if(listWrapper.getResult().size() > 0){
                return "该联系人信息已在资源中心中存在,无需再次添加!";
            }
            cscContantAndCompanyDto.setUserId(authResDtoByToken.getUserId());
            cscContantAndCompanyDto.setUserName(authResDtoByToken.getGroupRefName());
            cscContantAndCompanyDto.setGroupId(authResDtoByToken.getGroupId());
            *//*cscContantAndCompanyDto.getCscContact().setProvince("ofc001");
            cscContantAndCompanyDto.getCscContact().setCity("ofc001");
            cscContantAndCompanyDto.getCscContact().setArea("ofc001");
            cscContantAndCompanyDto.getCscContact().setStreet("ofc001");*//*
            *//**
             * 校验地址编码和名称是否完整
             *//*
            String provinceCode = cscContantAndCompanyDto.getCscContact().getProvince();
            String provinceName = cscContantAndCompanyDto.getCscContact().getProvinceName();
            String cityCode = cscContantAndCompanyDto.getCscContact().getCity();
            String cityName = cscContantAndCompanyDto.getCscContact().getCityName();
            String areaCode = cscContantAndCompanyDto.getCscContact().getArea();
            String areaName = cscContantAndCompanyDto.getCscContact().getAreaName();
            String streetCode = cscContantAndCompanyDto.getCscContact().getStreet();
//            String streetName = cscContantAndCompanyDto.getCscContact().getStreetName();
            if(PubUtils.isSEmptyOrNull(provinceCode) || PubUtils.isSEmptyOrNull(provinceName)
                    || PubUtils.isSEmptyOrNull(cityCode) || PubUtils.isSEmptyOrNull(cityName)
                    || PubUtils.isSEmptyOrNull(areaCode) || PubUtils.isSEmptyOrNull(areaName)
                   *//* || PubUtils.isSEmptyOrNull(streetCode) || PubUtils.isSEmptyOrNull(streetName)*//*){
                throw new BusinessException("联系人地址不完整");
            }



            Wrapper<?> wrapper = feignCscCustomerAPIClient.addCscContantAndCompany(cscContantAndCompanyDto);
            if(Wrapper.ERROR_CODE == wrapper.getCode()){
                throw new BusinessException(wrapper.getMessage());
            }
        }catch (Exception ex){
            throw new BusinessException(ex.getMessage());
        }
        return Wrapper.SUCCESS_MESSAGE;
    }*/
    /**
     * 下单或编辑时保存供应商及供应商联系人
     */
   /* public String saveSupportMessage(CscSupplierInfoDto cscSupplierInfoDto,String custId,AuthResDto authResDtoByToken){
        if(null == cscSupplierInfoDto){
            throw new BusinessException( "未添加供应商信息");
        }
        try {
            String provinceCode = cscSupplierInfoDto.getProvince();
            String provinceName = cscSupplierInfoDto.getProvinceName();
            String cityCode = cscSupplierInfoDto.getCity();
            String cityName = cscSupplierInfoDto.getCityName();
            String areaCode = cscSupplierInfoDto.getArea();
            String areaName = cscSupplierInfoDto.getAreaName();
            String streetCode = cscSupplierInfoDto.getStreet();
//            String streetName = cscSupplierInfoDto.getStreetName();
            if(PubUtils.isSEmptyOrNull(provinceCode) || PubUtils.isSEmptyOrNull(provinceName)
                    || PubUtils.isSEmptyOrNull(cityCode) || PubUtils.isSEmptyOrNull(cityName)
                    || PubUtils.isSEmptyOrNull(areaCode) || PubUtils.isSEmptyOrNull(areaName)
                    *//*|| PubUtils.isSEmptyOrNull(streetCode) || PubUtils.isSEmptyOrNull(streetName)*//*){
                throw new BusinessException("联系人地址不完整");
            }
            cscSupplierInfoDto.setCustomerId(custId);
            Wrapper<List<CscSupplierInfoDto>> listWrapper = null;
            try{
                listWrapper = feignCscSupplierAPIClient.querySupplierByAttribute(cscSupplierInfoDto);
            }catch (Exception ex){
                if(Wrapper.ERROR_CODE != listWrapper.getCode()){
                    return listWrapper.getMessage();
                }
            }
            if(listWrapper.getResult().size() > 0){
                return "该供应商信息已在资源中心中存在,无需再次添加!";
            }
            cscSupplierInfoDto.setSupplierCode("spofc" + System.currentTimeMillis());
            cscSupplierInfoDto.setUserId(authResDtoByToken.getUserId());
            cscSupplierInfoDto.setUserName(authResDtoByToken.getGroupRefName());
            cscSupplierInfoDto.setGroupId(authResDtoByToken.getGroupId());
            Wrapper<?> wrapper = feignCscSupplierAPIClient.addSupplierBySupplierCode(cscSupplierInfoDto);
            if(Wrapper.ERROR_CODE == wrapper.getCode()){
                throw new BusinessException(wrapper.getMessage());
            }
        }catch (Exception ex){
            throw new BusinessException("添加供应商信息出错!");
        }
        return Wrapper.SUCCESS_MESSAGE;
    }*/
    /**
     * 下单或编辑时在订单中心为用户保存订单中的仓库信息
     */
    private String saveWarehouseMessage(OfcWarehouseInformation ofcWarehouseInformation){
        if(null == ofcWarehouseInformation){
            return "未添加仓库信息";
        }
        try {
            int save = ofcWarehouseInformationService.save(ofcWarehouseInformation);
            if(save == 0){
                throw new BusinessException("保存仓库信息失败");
            }
        }catch (Exception ex){
            throw new BusinessException("保存仓库信息失败!", ex);
        }
        return Wrapper.SUCCESS_MESSAGE;
    }


    public Wrapper<?> validateFundamentalMessage(OfcOrderDTO ofcOrderDTO){
        if(null == ofcOrderDTO.getOrderTime()){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"请选择订单日期");
        }
        return WrapMapper.wrap(Wrapper.SUCCESS_CODE);
    }
    /**
     * 城配开单下单
     */
    private void distributionOrderPlace(OfcFundamentalInformation ofcFundamentalInformation,
                                        List<OfcGoodsDetailsInfo> ofcGoodsDetailsInfos,
                                        OfcDistributionBasicInfo ofcDistributionBasicInfo,
                                        OfcWarehouseInformation ofcWarehouseInformation,
                                        OfcFinanceInformation ofcFinanceInformation,String custId,
                                        CscContantAndCompanyDto cscContantAndCompanyDtoConsignor,
                                        CscContantAndCompanyDto cscContantAndCompanyDtoConsignee,
                                        AuthResDto authResDtoByToken,
                                        OfcOrderStatus ofcOrderStatus,
                                        OfcMerchandiser ofcMerchandiser) {
        int custOrderCode = 0;
        StringBuffer notes = new StringBuffer();
        if(!PubUtils.isSEmptyOrNull(ofcFundamentalInformation.getCustOrderCode())){
            custOrderCode = ofcFundamentalInformationService.checkCustOrderCode(ofcFundamentalInformation);
        }

        if (custOrderCode < 1){//根据客户订单编号查询唯一性
            ofcFundamentalInformation.setOrderCode(codeGenUtils.getNewWaterCode("SO",6));
            //"SO"+ PrimaryGenerater.getInstance()
            //        .generaterNextNumber(PrimaryGenerater.getInstance().getLastNumber())
            ofcFundamentalInformation.setCustCode(custId);
            if(PubUtils.isSEmptyOrNull(ofcFundamentalInformation.getCustName())){
                QueryCustomerCodeDto queryCustomerCodeDto = new QueryCustomerCodeDto();
                queryCustomerCodeDto.setCustomerCode(custId);
                Wrapper<CscCustomerVo> cscCustomerVo = (Wrapper<CscCustomerVo>)cscCustomerEdasService.queryCustomerByCustomerCodeOrId(queryCustomerCodeDto);
                if(Wrapper.ERROR_CODE == cscCustomerVo.getCode()){
                    throw new BusinessException(cscCustomerVo.getMessage());
                }else if(null == cscCustomerVo.getResult()){
                    throw new BusinessException("客户中心没有查到该客户!");
                }
                ofcFundamentalInformation.setCustName(cscCustomerVo.getResult().getCustomerName());
            }
            ofcFundamentalInformation.setAbolishMark(ORDERWASNOTABOLISHED);//未作废
            //添加该订单的货品信息 modify by wangst 做抽象处理
            BigDecimal goodsAmountCount = saveDetails(ofcGoodsDetailsInfos,ofcFundamentalInformation);
            ofcDistributionBasicInfo.setQuantity(goodsAmountCount);
            if (ofcFundamentalInformation.getOrderType().equals(WAREHOUSEDISTRIBUTIONORDER)){

                if(null == ofcWarehouseInformation.getProvideTransport()){
                    ofcWarehouseInformation.setProvideTransport(WAREHOUSEORDERNOTPROVIDETRANS);
                }
                if(ofcWarehouseInformation.getProvideTransport()== WAREHOUSEORDERPROVIDETRANS){
                    String consingneeSerialNo = cscContantAndCompanyDtoConsignee.getCscContactDto().getSerialNo();
                    if(null == consingneeSerialNo){
                        throw new BusinessException("该收货方联系人编码为空");
                    }
                    cscContantAndCompanyDtoConsignee.getCscContactDto().setSerialNo(consingneeSerialNo.split("\\@")[0]);
                    Wrapper<?> wrapper = validateDistrictContactMessage(cscContantAndCompanyDtoConsignor, cscContantAndCompanyDtoConsignee);
                    if(Wrapper.ERROR_CODE == wrapper.getCode()){
                        throw new BusinessException(wrapper.getMessage());
                    }
                    //校验运输基本信息
                    checkDistibutionBaseMsg(ofcDistributionBasicInfo);


                    addDistributionInfo(ofcDistributionBasicInfo, ofcFundamentalInformation);
                            /*saveContactMessage(cscContantAndCompanyDtoConsignor,custId,authResDtoByToken);
                            saveContactMessage(cscContantAndCompanyDtoConsignee,custId,authResDtoByToken);*/
                }
                // 更新仓配信息
                upOfcWarehouseInformation(ofcWarehouseInformation,ofcFundamentalInformation);
                String businessTypeHead = ofcFundamentalInformation.getBusinessType().substring(0,2);
                if("62".equals(businessTypeHead)){//如果是入库才有供应商信息//这儿才是入库
                            /*Wrapper<?> wrapper = validateSupportContactMessage(cscSupplierInfoDto);
                            if(Wrapper.ERROR_CODE == wrapper.getCode()){
                                throw new BusinessException(wrapper.getMessage());
                            }*/
                    // saveSupportMessage(cscSupplierInfoDto,custId,authResDtoByToken);

                }
                ofcWarehouseInformationService.save(ofcWarehouseInformation);
                if("61".equals(businessTypeHead)){//如果是入库才有供应商信息//这儿是出库
                    ofcWarehouseInformation.setSupportCode("");
                    ofcWarehouseInformation.setSupportName("");
                }
            }else if(ofcFundamentalInformation.getOrderType().equals(TRANSPORTORDER)){
                Wrapper<?> wrapper = validateDistrictContactMessage(cscContantAndCompanyDtoConsignor, cscContantAndCompanyDtoConsignee);
                if(Wrapper.ERROR_CODE == wrapper.getCode()){
                    throw new BusinessException(wrapper.getMessage());
                }
                //校验运输基本信息
                checkDistibutionBaseMsg(ofcDistributionBasicInfo);
                //运输订单
                //设置城配
                ofcFundamentalInformation.setBusinessType(OrderConstConstant.WITHTHECITY);
                // ofcFundamentalInformation.setBusinessType(OrderConstEnum.WITHTHEKABAN);
                //保存运输信息
                addDistributionInfo(ofcDistributionBasicInfo, ofcFundamentalInformation);
                        /*saveContactMessage(cscContantAndCompanyDtoConsignor,custId,authResDtoByToken);
                        saveContactMessage(cscContantAndCompanyDtoConsignee,custId,authResDtoByToken);*/
            }else{
                throw new BusinessException("您选择的订单类型系统无法识别!");
            }
            notes.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            notes.append(" 订单已创建");
            notes.append(" 操作人: ").append(authResDtoByToken.getUserName());
            notes.append(" 操作单位: ").append(authResDtoByToken.getGroupRefName());
            ofcOrderStatus.setNotes(notes.toString());
            upOrderStatus(ofcOrderStatus,ofcFundamentalInformation,authResDtoByToken);
            addFinanceInformation(ofcFinanceInformation,ofcFundamentalInformation);
            //添加基本信息
            ofcFundamentalInformationService.save(ofcFundamentalInformation);
            if(ofcMerchandiserService.select(ofcMerchandiser).size()==0 && !PubUtils.trimAndNullAsEmpty(ofcMerchandiser.getMerchandiser()).equals("")){
                ofcMerchandiserService.save(ofcMerchandiser);
            }
            if(!PubUtils.isSEmptyOrNull(ofcFundamentalInformation.getOrderBatchNumber())){
                //进行自动审核
                ofcOrderManageService.orderAutoAuditFromOperation(ofcFundamentalInformation,ofcGoodsDetailsInfos,ofcDistributionBasicInfo,
                        ofcWarehouseInformation,ofcFinanceInformation,ofcOrderStatus.getOrderStatus(),"review",authResDtoByToken);
            }

        }else{
            throw new BusinessException("客户订单编号" + ofcFundamentalInformation.getCustOrderCode() + "已经存在!您不能重复下单!");
        }
    }

    /**
     * 校验收发货方信息
     * @param cscContantAndCompanyDtoConsignor
     * @param cscContantAndCompanyDtoConsignee
     * @return
     */
    private Wrapper<?> validateDistrictContactMessage(CscContantAndCompanyDto cscContantAndCompanyDtoConsignor, CscContantAndCompanyDto cscContantAndCompanyDtoConsignee){
        if(null == cscContantAndCompanyDtoConsignor || null == cscContantAndCompanyDtoConsignee){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"校验收货方信息入参为空");
        }
        if(null == cscContantAndCompanyDtoConsignor.getCscContactCompanyDto() || null == cscContantAndCompanyDtoConsignee.getCscContactCompanyDto()){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"校验收货方信息入参收货方信息为空");
        }
        if(null == cscContantAndCompanyDtoConsignor.getCscContactDto() || null == cscContantAndCompanyDtoConsignee.getCscContactDto()){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"校验收货方信息入参收货方联系人信息为空");
        }
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignor.getCscContactCompanyDto().getContactCompanyName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"请输入发货方信息");
        }
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignor.getCscContactDto().getContactName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"发货方联系人名称未填写");
        }
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignor.getCscContactDto().getPhone())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"发货方联系人电话未填写");
        }
        //二级地址还需特殊处理
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignor.getCscContactDto().getProvinceName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"发货方联系人地址未选择");
        }
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignor.getCscContactDto().getCityName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"发货方联系人地址不完整");
        }
        /*if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignor.getCscContact().getAreaName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"发货方联系人地址不完整");
        }*/

        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignee.getCscContactCompanyDto().getContactCompanyName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"请输入收货方信息");
        }
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignee.getCscContactDto().getContactName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"收货方联系人名称未填写");
        }
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignee.getCscContactDto().getPhone())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"收货方联系人电话未填写");
        }
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignee.getCscContactDto().getProvinceName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"收货方联系人地址未选择");
        }
        if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignee.getCscContactDto().getCityName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"收货方联系人地址不完整");
        }
        /*if(PubUtils.isSEmptyOrNull(cscContantAndCompanyDtoConsignee.getCscContact().getAreaName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"收货方联系人地址不完整");
        }*/

        return WrapMapper.wrap(Wrapper.SUCCESS_CODE);
    }

    /**
     * 校验供应商联系人
     */
    public Wrapper<?> validateSupportContactMessage(CscSupplierInfoDto cscSupplierInfoDto){
        if(null == cscSupplierInfoDto){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"校验供应商联系人入参为空");
        }

        if(PubUtils.isSEmptyOrNull(cscSupplierInfoDto.getSupplierName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"请输入供应商信息");
        }
        if(PubUtils.isSEmptyOrNull(cscSupplierInfoDto.getContactName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"供应商联系人名称未填写");
        }
        if(PubUtils.isSEmptyOrNull(cscSupplierInfoDto.getContactPhone())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"供应商联系人电话未填写");
        }
        if(PubUtils.isSEmptyOrNull(cscSupplierInfoDto.getProvinceName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"收货方联系人地址未选择");
        }
        if(PubUtils.isSEmptyOrNull(cscSupplierInfoDto.getCityName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"供应商联系人地址不完整");
        }
        /*if(PubUtils.isSEmptyOrNull(cscSupplierInfoDto.getAreaName())){
            return WrapMapper.wrap(Wrapper.ERROR_CODE,"供应商联系人地址不完整");
        }*/
        return WrapMapper.wrap(Wrapper.SUCCESS_CODE);

    }
    /**
     * 校验运输基本信息
     */
    private void checkDistibutionBaseMsg(OfcDistributionBasicInfo ofcDistributionBasicInfo){
        /*String volume = ofcDistributionBasicInfo.getCubage();
        if(!PubUtils.isSEmptyOrNull(volume)){
            boolean matches = volume.matches("\\d{1,10}\\*\\d{1,10}\\*\\d{1,10}");
            if(!matches){
                throw new BusinessException("您输入的体积不符合规则! 请重新输入!");
            }
        }*/


    }




}

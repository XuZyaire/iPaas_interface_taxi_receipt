package com.bizcloud.function;

import com.alibaba.fastjson.JSONObject;
import com.baidu.aip.contentcensor.EImgType;
import com.baidu.aip.ocr.AipOcr;

import com.bizcloud.ipaas.t97f51c2f74cf4b30bced948079b4d0fd.d20210407113259.auth.extension.AuthConfig;
import com.bizcloud.ipaas.t97f51c2f74cf4b30bced948079b4d0fd.d20210407113259.codegen.TclsqingApi;
import com.bizcloud.ipaas.t97f51c2f74cf4b30bced948079b4d0fd.d20210407113259.codegen.TfymxiApi;
import com.bizcloud.ipaas.t97f51c2f74cf4b30bced948079b4d0fd.d20210407113259.model.*;

import com.google.gson.Gson;
import com.sun.org.apache.xpath.internal.operations.Bool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelloFunction {
    //设置APPID/AK/SK
    public static final String APP_ID = "23912083";
    public static final String API_KEY = "FGtUDEUfQOsvVojtOBg6GUrC";
    public static final String SECRET_KEY = "DFRnIRqtNrfpi3jgOMTvVPMVSPZg0zKq";

    public static Gson gson = new Gson();
    
    //差旅申请
    public static TclsqingApi tclsqingApi = new TclsqingApi();
    //差旅明细
    public static TfymxiApi tfymxiApi = new TfymxiApi();

    public Object handle(Object param,Map<String,String> variables){
        /* 获取ACCESS 权限 */
        AuthConfig authConfig = new AuthConfig(variables.get("APAAS_ACCESS_KEY"), variables.get("APAAS_ACCESS_SECRET"));
        authConfig.initAuth();
        
        //初始化AipOcr
        AipOcr client = new AipOcr(APP_ID,API_KEY,SECRET_KEY);

        // 传入可选参数调用接口
        HashMap<String, String> options = new HashMap<String, String>();

        try {
            //获取传入参数
            String inClass =JSONObject.toJSONString(param);
            JSONObject json_inClass = JSONObject.parseObject(inClass);

            //获取对象
            String t_clsQing = json_inClass.get("t_CLSQing").toString();
            JSONObject json_t_clsQing = JSONObject.parseObject(t_clsQing);
            //获取id
            String id = json_t_clsQing.get("id").toString();

            TCLSQingDTO query = new TCLSQingDTO();
            query.setId(id);
            List<TCLSQingDTOResponse> list = tclsqingApi.findtCLSQingUsingPOST(query).getData();

            //获取该差旅申请下所有差旅明细单号
            List list_dHao = getDHao(id);

            //将list转换为json格式
            String list_data = gson.toJson(list.get(0));
            JSONObject json_list = JSONObject.parseObject(list_data);

            //获取附件集合
            String scTaxiFP = json_list.get("SCTaxiFP").toString();
            List list_scTaxi = JSONObject.parseArray(scTaxiFP);
            for (int i=0;i<list_scTaxi.size();i++){
                //差旅费用明细
                TFYMXiDTOUpdate update = new TFYMXiDTOUpdate();
                //保存信息
                SaveOrUpdatetFYMXiDTO saveOrUpdatetFYMXiDTO = new SaveOrUpdatetFYMXiDTO();

                String scTaxi_data = gson.toJson(list_scTaxi.get(i));
                JSONObject json_scTaxi = JSONObject.parseObject(scTaxi_data);
                //获取地址
                String filePath = json_scTaxi.get("filePath").toString();

                //识别发票
                org.json.JSONObject res = client.taxiReceipt(filePath, EImgType.URL, options);

                //获取识别结果
                String words_result = res.get("words_result").toString();
                JSONObject json_words_result = JSONObject.parseObject(words_result);

                //获取发票号码
                String invoiceNum = json_words_result.get("InvoiceCode").toString();
                //判断发票号码是否重复
                Boolean ifRepeat = ifRepeat(list_dHao, invoiceNum);
                System.out.println(ifRepeat);
                if (ifRepeat){
                    //获取总金额
                    String fare = json_words_result.get("Fare").toString();
                    BigDecimal money = new BigDecimal(fare);

                    //设置关联差旅申请
                    update.setClSQing(id);
                    //设置名称
                    update.setName("出租车费用");
                    //设置单号
                    update.setDhao(invoiceNum);
                    //设置金额
                    update.setJinE(money);

                    //保存信息
                    saveOrUpdatetFYMXiDTO.setUpdate(update);
                    TFYMXiSaveOrUpdateDataResponseObject responseObject = tfymxiApi.saveOrUpdatetFYMXiUsingPOST(saveOrUpdatetFYMXiDTO);
                    System.out.println(responseObject.getMessage());
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     *查询对应差旅申请下的差旅明细单号
     * @param id
     * @return
     */
    public static List getDHao(String id){
        //创建存放单号的集合
        List list_dHao = new ArrayList();
        //查询到该对应的差旅申请下所有的差旅明细
        TFYMXiDTO query = new TFYMXiDTO();
        query.setClSQing(id);
        DataResponseListtFYMXiDTO response = tfymxiApi.findtFYMXiUsingPOST(query);
        List<TFYMXiDTOResponse> responseList = response.getData();
        //循环获取其下的单号
        for (int i = 0; i < responseList.size(); i++) {
            String data = gson.toJson(responseList.get(i));
            JSONObject json_list = JSONObject.parseObject(data);
            //获取其中的单号
            String dHao = json_list.get("DHao").toString();
            //存放到list中
            list_dHao.add(dHao);
        }
        return list_dHao;
    }

    /**
     * 判断单号是否重复
     * @return
     */
    public static Boolean ifRepeat(List list_DHao,String dHao){
        Boolean flag = true;
        for (int i = 0; i < list_DHao.size(); i++) {
            if (list_DHao.get(i).equals(dHao)){
                //重复
                flag = false;
                break;
            }else {
                //不重复
                flag = true;
                continue;
            }
        }
        return flag;
    }
}

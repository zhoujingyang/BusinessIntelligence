package com.business.intelligence.crawler.eleme;

import com.business.intelligence.dao.ElemeDao;
import com.business.intelligence.model.Authenticate;
import com.business.intelligence.model.ElemeModel.ElemeActivity;
import com.business.intelligence.util.WebUtils;
import com.google.common.collect.Maps;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * Created by Tcqq on 2017/7/26.
 * 商店活动 POST请求
 */
@Component
public class ElemeActivityCrawler extends ElemeCrawler {
    //默认抓取前一天的，具体值已经在父类设置
    private Date crawlerDate = super.crawlerDate;
    //用户信息
    private Authenticate authenticate;
    @Autowired
    private ElemeDao elemeDao;
    private HttpClient httpClient = super.httpClient;

    private static final String URL ="https://app-api.shop.ele.me/marketing/invoke/?method=applyActivityManage.getApplyActivity";
    private static final Map<Boolean, String> SHARE = Maps.newHashMap();

    static {
        SHARE.put(true,"与折扣、特价活动共享");
        SHARE.put(false,"不与折扣、特价活动共享");
        SHARE.put(null,"未知");
    }

    @Override
    public void doRun() {

    }


    public static void main(String[] args) {
        ElemeActivityCrawler elemeActivityCrawler = new ElemeActivityCrawler();
        List<LinkedHashMap<String, Object>> activityText = elemeActivityCrawler.getActivityText(elemeActivityCrawler.login());
        List<ElemeActivity> elemeActivityBeans = elemeActivityCrawler.getElemeActivityBeans(activityText);
        for(ElemeActivity e : elemeActivityBeans){
            System.out.println(e);
        }
    }


    /**
     * 通过爬虫获得所有活动详情
     * @param client
     * @return
     */
    public List<LinkedHashMap<String, Object>> getActivityText(CloseableHttpClient client){
        CloseableHttpResponse execute = null;
        HttpPost post = new HttpPost(URL);
        StringEntity jsonEntity = null;
        String json = "{\"id\":\"9368dd8a-a6e9-4e6c-855c-3d2e29ff3498\",\"method\":\"getApplyActivity\",\"service\":\"applyActivityManage\",\"params\":{\"shopId\":150148671},\"metas\":{\"appName\":\"melody\",\"appVersion\":\"4.4.0\",\"ksid\":\"Mzg3OTI4ZDMtODMzNy00MDUyLWE2ZjN2FhYT\"},\"ncp\":\"2.0.0\"}";
        jsonEntity = new StringEntity(json, "UTF-8");
        post.setEntity(jsonEntity);
        setElemeHeader(post);
        post.setHeader("X-Eleme-RequestID", "9368dd8a-a6e9-4e6c-855c-3d2e29ff3498");
        try {
            execute = client.execute(post);
            HttpEntity entity = execute.getEntity();
            String result = EntityUtils.toString(entity, "UTF-8");
            List<LinkedHashMap<String, Object>> list = WebUtils.getMapsByJsonPath(result, "$.result");
            return list;
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (execute != null){
                    execute.close();
                }
                if(client != null){
                    client.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return null;
    }


    /**
     * 获取ElemeActivity实体类
     * @param activityList
     * @return
     */
    public List<ElemeActivity> getElemeActivityBeans(List<LinkedHashMap<String, Object>> activityList){
        List<ElemeActivity> list = new ArrayList<>();
        for(LinkedHashMap<String,Object> map : activityList){
            LinkedHashMap<String, Object> contentMap = (LinkedHashMap)map.get("content");
            ElemeActivity  elemeActivity= new ElemeActivity();
            elemeActivity.setShopId(150148671l);
            elemeActivity.setBeginDate((String)map.get("beginDate"));
            elemeActivity.setEndDate((String)map.get("endDate"));
            elemeActivity.setName((String)map.get("name"));
            elemeActivity.setStatus((String)map.get("status"));
            elemeActivity.setCreateTime((String)map.get("createdAt"));
            elemeActivity.setDescription((String)map.get("description"));
            elemeActivity.setShare(SHARE.get((Boolean)contentMap.get("shareWithOtherActivities")));
            //活动内容
            String content = "";
            switch ((String) map.get("iconText")){
                case "折":
                   content = getZhe((Integer)contentMap.get("minCategory"),(Integer)contentMap.get("maxCategory"),(Double)contentMap.get("originalLeastPrice"),(Double)contentMap.get("originalMostPrice"),(Double)contentMap.get("discount"));
                   break;
                case "减":
                   for(LinkedHashMap<String, Object> items : (List<LinkedHashMap<String, Object>>)contentMap.get("items")){
                       content = content+getJian((Integer)items.get("condition"),(Integer)items.get("discount"),(Double)items.get("subsidy"),(Integer)items.get("onlinePaymentDiscount"),(Double)items.get("onlinePaymentSubsidy"));
                   }
                   break;
                case "赠":
                    for(LinkedHashMap<String, Object> gift: (List<LinkedHashMap<String, Object>>)contentMap.get("giftContents")){
                        content = content+getZeng((Integer)gift.get("condition"),(String)((LinkedHashMap<String, Object>)gift.get("giftActivityBenefit")).get("name"),(Integer) ((LinkedHashMap<String, Object>)gift.get("giftActivityBenefit")).get("quantity"));
                    }
                   break;
                case "惠":
                    content = getHui((Integer) contentMap.get("minCategory"),(Integer) contentMap.get("maxCategory"),(Double) contentMap.get("originalLeastPrice"),(Double) contentMap.get("originalMostPrice"),(Double)contentMap.get("lockPrice"));
                    break;
                default:
                   content = "无";
            }
            elemeActivity.setContent(content);
            list.add(elemeActivity);
        }
        return list;
    }

    /**
     * 当优惠种类为"折"时，拼接折的活动内容
     * @param minCategory
     * @param maxCategory
     * @param originalLeastPrice
     * @param originalMostPrice
     * @param discount
     * @return
     */
    public String getZhe(Integer minCategory,Integer maxCategory,Double originalLeastPrice,Double originalMostPrice,Double discount){
        StringBuilder sb = new StringBuilder();
        sb.append("选择")
                .append(minCategory)
                .append("-")
                .append(maxCategory)
                .append("种原价为")
                .append(originalLeastPrice)
                .append("-")
                .append(originalMostPrice)
                .append("元的商品。统一")
                .append(discount*10)
                .append("折出售，平台按原价补贴1折（每单最多补贴20元）");
        return sb.toString();
    }

    /**
     * 当优惠种类为"减"时，拼接减的活动内容
     * @param condition
     * @param discount
     * @param subsidy
     * @param onlinePaymentDiscount
     * @param onlinePaymentSubsidy
     * @return
     */
    public String getJian(Integer condition,Integer discount,Double subsidy,Integer onlinePaymentDiscount,Double onlinePaymentSubsidy){
        StringBuilder sb = new StringBuilder();
        sb.append("满")
                .append(condition)
                .append("减")
                .append(discount)
                .append("，平台补贴")
                .append(subsidy)
                .append("元。在线支付再减")
                .append(onlinePaymentDiscount)
                .append("元，平台再补贴")
                .append(onlinePaymentSubsidy)
                .append("元");
        return sb.toString();
    }

    /**
     * 当优惠种类为"赠"时，拼接赠的活动内容
     * @param condition
     * @param name
     * @param quantity
     * @return
     */
    public String getZeng(Integer condition,String name,Integer quantity){
        StringBuilder sb = new StringBuilder();
        sb.append("下单满")
                .append(condition)
                .append("元，赠")
                .append(name)
                .append(quantity)
                .append("份");
        return sb.toString();
    }

    /**
     * 当优惠种类为"惠"时，拼接惠的活动内容
     * @param minCategory
     * @param maxCategory
     * @param originalLeastPrice
     * @param originalMostPrice
     * @param lockPrice
     * @return
     */
    public String getHui(Integer minCategory,Integer maxCategory,Double originalLeastPrice,Double originalMostPrice,Double lockPrice){
        StringBuilder sb = new StringBuilder();
        sb.append("选择")
                .append(minCategory)
                .append("-")
                .append(maxCategory)
                .append("种原价为")
                .append(originalLeastPrice)
                .append("-")
                .append(originalMostPrice)
                .append("元的商品。统一售价")
                .append(lockPrice)
                .append("元");
        return sb.toString();
    }


}

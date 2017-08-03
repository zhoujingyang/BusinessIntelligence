package com.business.intelligence.crawler.eleme;

import com.business.intelligence.dao.ElemeDao;
import com.business.intelligence.model.Authenticate;
import com.business.intelligence.model.ElemeModel.ElemeBill;
import com.business.intelligence.model.ElemeModel.ElemeEvaluate;
import com.business.intelligence.util.DateUtils;
import com.business.intelligence.util.WebUtils;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created by Tcqq on 2017/7/17.
 * 顾客评价 POST请求
 */
@Component
public class ElemeEvaluateCrawler extends ElemeCrawler {
    //默认抓取前一天的，具体值已经在父类设置
    private Date crawlerDate = super.crawlerDate;
    private Date endCrawlerDate = org.apache.commons.lang3.time.DateUtils.addDays(crawlerDate,1);
    //用户信息
    private Authenticate authenticate;
    @Autowired
    private ElemeDao elemeDao;

    private static final String URL = "https://app-api.shop.ele.me/ugc/invoke?method=shopRating.querySingleShopRating";

    @Override
    public void doRun() {
        String evaluateText = getEvaluateText(login());
        List<LinkedHashMap<String, Object>> orderList = getOrderList(evaluateText);
        List<LinkedHashMap<String, Object>> foodList = getFoodList(evaluateText);
        List<ElemeEvaluate> elemeEvaluateBeans = getElemeEvaluateBeans(orderList, foodList);
        for (ElemeEvaluate elemeEvaluate : elemeEvaluateBeans){
            elemeDao.insertEvaluate(elemeEvaluate);
        }

    }

    /**
     * 通过爬虫获得所有的对应日期的顾客评论
     * @param client
     * @return
     */
    public String getEvaluateText(CloseableHttpClient client){
        CloseableHttpResponse execute = null;
        HttpPost post = new HttpPost(URL);
        StringEntity jsonEntity = null;
        String beginDate = DateUtils.date2String(crawlerDate);
        String endDate = DateUtils.date2String(endCrawlerDate);
        String json = "{\"id\":\"4b6e096e-0d39-49a6-adb7-c6fe3b6583b4\",\"method\":\"querySingleShopRating\",\"service\":\"shopRating\",\"params\":{\"shopId\":"+SHOPID+",\"query\":{\"beginDate\":\""+beginDate+"T00:00:00\",\"endDate\":\""+endDate+"T00:00:00\",\"hasContent\":null,\"level\":null,\"replied\":null,\"tag\":null,\"limit\":20,\"offset\":0,\"state\":null,\"deadline\":{\"name\":\"昨日\",\"count\":1,\"value\":\"-1\",\"$$hashKey\":\"object:2467\"}}},\"metas\":{\"appName\":\"melody\",\"appVersion\":\"4.4.0\",\"ksid\":\"ZTRkYWJlODQtMzZhZi00MmU1LWFjYTMTE2Zm\"},\"ncp\":\"2.0.0\"}";
        jsonEntity = new StringEntity(json, "UTF-8");
        post.setEntity(jsonEntity);
        setElemeHeader(post);
        post.setHeader("X-Eleme-RequestID", "4b6e096e-0d39-49a6-adb7-c6fe3b6583b4");
        try {
            execute = client.execute(post);
            HttpEntity entity = execute.getEntity();
            String result = EntityUtils.toString(entity, "UTF-8");

            Object count = WebUtils.getOneByJsonPath(result, "$.result.total");
            json = "{\"id\":\"4b6e096e-0d39-49a6-adb7-c6fe3b6583b4\",\"method\":\"querySingleShopRating\",\"service\":\"shopRating\",\"params\":{\"shopId\":"+SHOPID+",\"query\":{\"beginDate\":\""+beginDate+"T00:00:00\",\"endDate\":\""+endDate+"T00:00:00\",\"hasContent\":null,\"level\":null,\"replied\":null,\"tag\":null,\"limit\":"+(Integer)count+",\"offset\":0,\"state\":null,\"deadline\":{\"name\":\"昨日\",\"count\":1,\"value\":\"-1\",\"$$hashKey\":\"object:2467\"}}},\"metas\":{\"appName\":\"melody\",\"appVersion\":\"4.4.0\",\"ksid\":\"ZTRkYWJlODQtMzZhZi00MmU1LWFjYTMTE2Zm\"},\"ncp\":\"2.0.0\"}";
            jsonEntity = new StringEntity(json, "UTF-8");
            post.setEntity(jsonEntity);
            execute = client.execute(post);
            entity = execute.getEntity();
            result = EntityUtils.toString(entity, "UTF-8");
            return result;
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
     * 通过爬取的json获得订单的评论详情
     * @param json
     * @return
     */
    public List<LinkedHashMap<String, Object>> getOrderList(String json){
        List<LinkedHashMap<String, Object>> foodList = WebUtils.getMapsByJsonPath(json, "$.result.orderRatingList[*].orderCommentList[*]");
        return foodList;
    }

    /**
     * 通过爬取的json获得具体商品的评论详情
     * @param json
     * @return
     */
    public List<LinkedHashMap<String, Object>> getFoodList(String json){
        List<LinkedHashMap<String, Object>> foodList = WebUtils.getMapsByJsonPath(json, "$.result.orderRatingList[*].foodCommentList[*]");
        return foodList;
    }

    /**
     * 获取ElemeEvaluate实体类
     * @param orderList
     * @param foodList
     * @return
     */
    public List<ElemeEvaluate> getElemeEvaluateBeans(List<LinkedHashMap<String, Object>> orderList , List<LinkedHashMap<String, Object>> foodList){
        List<ElemeEvaluate> list = new ArrayList<>();
        for(LinkedHashMap<String,Object> map : orderList){
            ElemeEvaluate elemeEvaluate = new ElemeEvaluate();
            elemeEvaluate.setId(map.getOrDefault("ratingId",null) == null ? null : (Integer)map.getOrDefault("ratingId",null)*1l);
            elemeEvaluate.setShopId(204666l);
            elemeEvaluate.setCrawlerDate(DateUtils.date2String(crawlerDate));
            elemeEvaluate.setEvaValue((String)map.getOrDefault("ratingContent","无评论"));
            elemeEvaluate.setQuality(String.valueOf(map.getOrDefault("ratingStar","无")));
            elemeEvaluate.setGoods("本条为订单评论");
            list.add(elemeEvaluate);
        }
        for(LinkedHashMap<String,Object> map : foodList){
            ElemeEvaluate elemeEvaluate = new ElemeEvaluate();
            elemeEvaluate.setId((Long)map.getOrDefault("ratingId",null));
            elemeEvaluate.setShopId(204666l);
            elemeEvaluate.setCrawlerDate(DateUtils.date2String(crawlerDate));
            elemeEvaluate.setEvaValue((String)map.getOrDefault("foodRatingContent","无评论"));
            elemeEvaluate.setQuality((String)map.getOrDefault("quality","无"));
            elemeEvaluate.setGoods((String)map.getOrDefault("foodName","无"));
            list.add(elemeEvaluate);
        }
        return list;
    }



}

package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getTypeList() {
        List<String> typeList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        if (CollectionUtil.isNotEmpty(typeList)) {
            List<ShopType> types = new ArrayList<>();
            for (String type : typeList) {
                ShopType shopType = JSONUtil.toBean(type, ShopType.class);
                types.add(shopType);
            }
            return Result.ok(types);
        }

        // 不存在缓存,手动查询并存入缓存中
        List<ShopType> shopTypeList = this.query().orderByAsc("sort").list();
        if (shopTypeList == null) {
            return Result.fail("数据不存在!");
        }
        for (ShopType shopType : shopTypeList) {
            stringRedisTemplate.opsForList().rightPush(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopType));
        }
        return Result.ok(shopTypeList);
    }
}

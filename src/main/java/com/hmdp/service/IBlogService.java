package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    /**
     * 点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 点赞排行
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);
}

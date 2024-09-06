package com.example.demo.http;

import com.example.demo.bean.CreateGroupResult;
import com.example.demo.bean.FriendBean;
import com.example.demo.bean.GroupBean;
import com.example.demo.bean.HttpResult;
import com.example.demo.bean.ListResult;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface GroupsService {
    @GET("groups/mygroups")
    Call<HttpResult<ListResult<GroupBean>>> getGroupList(@Query("start_id") String startId, @Query("count") int count);


    @POST("groups/add")
    Call<HttpResult<CreateGroupResult>> createGroup(@Body RequestBody body);

    @POST("groups/members/add")
    Call<HttpResult<Object>> addMember(@Body RequestBody body);
}

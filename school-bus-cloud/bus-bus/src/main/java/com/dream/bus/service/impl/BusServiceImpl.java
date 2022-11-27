/**
 * @program school-bus-cloud
 * @description: BusServiceImpl
 * @author: mf
 * @create: 2020/11/08 13:25
 */

package com.dream.bus.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dream.bus.bus.param.*;
import com.dream.bus.common.CastException;
import com.dream.bus.common.DateUtil;
import com.dream.bus.common.RedisUtils;
import com.dream.bus.common.UUIDUtils;
import com.dream.bus.common.converter.BusConverter;
import com.dream.bus.common.converter.CountConverter;
import com.dream.bus.constants.RedisConstants;
import com.dream.bus.constants.SbCode;
import com.dream.bus.dao.BusMapper;
import com.dream.bus.dao.CountMapper;
import com.dream.bus.model.Bus;
import com.dream.bus.model.Count;
import com.dream.bus.service.IBusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
public class BusServiceImpl implements IBusService {

    @Autowired
    private BusMapper busMapper;
    @Autowired
    private CountMapper countMapper;
    @Autowired
    private BusConverter busConverter;
    @Autowired
    private CountConverter countConverter;
    @Autowired
    private RedisUtils redisUtils;

    @Override
    public PageBusResponse getBus(PageBusRequest request) {
        PageBusResponse response = new PageBusResponse();
        try {
            IPage<Bus> busIPage = new Page<>(request.getCurrentPage(), request.getPageSize());
            busIPage = busMapper.selectPage(busIPage, null);
            response.setCurrentPage(busIPage.getCurrent());
            response.setPageSize(busIPage.getSize());
            response.setPages(busIPage.getPages());
            response.setTotal(busIPage.getTotal());
            response.setBusDtos(busConverter.bus2List(busIPage.getRecords()));
            response.setCode(SbCode.SUCCESS.getCode());
            response.setMsg(SbCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(SbCode.DB_EXCEPTION.getCode());
            response.setMsg(SbCode.DB_EXCEPTION.getMessage());
            log.error("getBus:" , e);
            return response;
        }
        return response;
    }

    @Override
    public PageCountResponse getCount(PageCountRequest request) {
        PageCountResponse response = new PageCountResponse();
        try {
            IPage<CountSimpleDto> countIPage = new Page<>(request.getCurrentPage(), request.getPageSize());
            QueryWrapper<CountSimpleDto> queryWrapper = new QueryWrapper<>();
            // 获取时间
            String currHours = DateUtil.getHours();
            String day = DateUtil.getDay();
            String testDay = LocalDate.of(2020, 12, 22).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")).toString();
            System.out.println("测试世界======="+testDay);
            System.out.println("当前时间："+currHours);
            System.out.println("当前日期："+day);
            // 判断条件
            queryWrapper
                    .eq("begin_date", testDay)
                    .ge("begin_time", currHours)
                    .eq("bus_status", request.getBusStatus())
                    .orderByAsc("begin_time");// 时间

            countIPage = countMapper.selectCounts(countIPage, queryWrapper);
            response.setCurrentPage(countIPage.getCurrent());
            response.setPageSize(countIPage.getSize());
            response.setPages(countIPage.getPages());
            response.setTotal(countIPage.getTotal());
            response.setCountSimpleDtos(countIPage.getRecords());
            response.setCode(SbCode.SUCCESS.getCode());
            response.setMsg(SbCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(SbCode.DB_EXCEPTION.getCode());
            response.setMsg(SbCode.DB_EXCEPTION.getMessage());
            log.error("getCount:", e);
            return response;
        }
        return response;
    }

    @Override
    public CountDetailResponse getCountDetailById(CountDetailRequest request) {
        CountDetailResponse response = new CountDetailResponse();
        try {
            QueryWrapper<CountDetailDto> wrapper = new QueryWrapper<>();
            wrapper.eq("sc.uuid", request.getCountId());
            CountDetailDto countDetailDto = countMapper.selectCountDetailById(wrapper);
            response.setCountDetailDto(countDetailDto);
            response.setCode(SbCode.SUCCESS.getCode());
            response.setMsg(SbCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("getCountDetail", e);
            response.setCode(SbCode.DB_EXCEPTION.getCode());
            response.setMsg(SbCode.DB_EXCEPTION.getMessage());
            return response;
        }
        return response;
    }

    @Override
    public boolean repeatSeats(String seats, Long coundId) {
        // 查查数据库， 找到座位字段
        boolean b = false; // false:不重复，true：重复
        try {
            Count count = countMapper.selectById(coundId);
            // 比如，selectedSeats 是1,2
            // dbSeats：""，
            // dbSeats："1,2,3"，
            // dbSeats: "4,5"
            // 前端传来的selectedSeats， 前端判断是否为空，要不然后端也判断一下得了
            if (seats.equals("")) {
                return true;
            }
            if (count.getSelectedSeats().equals("")) {
                return false;
            }
            String[] ss = seats.split(",");
            String[] cs = count.getSelectedSeats().split(",");
            HashSet<String> hashSet = new HashSet<>(Arrays.asList(cs)); // 这步存在并发问题 值得优化的地方
            for (String s : ss) {
                if (hashSet.contains(s)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("selectedSeats", e);
            return true; // 异常就算是重复
        }
        return b;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean addSeats(String seats, Long coundId) throws RuntimeException{
        // 直接找场次的座位
        Count count = countMapper.selectById(coundId);
        String selectedSeats = count.getSelectedSeats();
        String newSelectedSeats = seats;
        if (!StrUtil.isEmpty(selectedSeats)) {
            newSelectedSeats = selectedSeats + "," + newSelectedSeats; // 这里可以优化，字符串拼接，这样的方式爆内存
        }
        count.setSelectedSeats(newSelectedSeats);
        countMapper.updateById(count);
//        CastException.cast(SbCode.SYSTEMBLOCK_ERROR);
//        throw new RuntimeException("haha");
        return true;
    }

    @Override
    public boolean filterRepeatSeats(String seats, Long coundId) {
        try {
            Count count = countMapper.selectById(coundId);
            String[] ss = seats.split(",");
            String[] cs = count.getSelectedSeats().split(",");
            HashSet<String> hashSet = new HashSet<>(Arrays.asList(cs)); // 这步存在并发问题 值得优化的地方
            for (String s : ss) {
                if (hashSet.contains(s)) {
                    hashSet.remove(s);
                }
            }
            if (hashSet.isEmpty()) {
                count.setSelectedSeats("");
            }
            StringBuffer sb = new StringBuffer();
            for (String s : hashSet) {
                sb.append(s);
                sb.append(",");
            }
            count.setSelectedSeats(sb.toString());
            countMapper.updateById(count);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("filterRepeatSeats:", e);
            return false;
        }
        return true;
    }

    @Override
    public void schedulChangeBusStatus() {
        // 获取时间
        String currTime = DateUtil.getHours();
        // 获取日期
        String day = DateUtil.getDay();
        log.warn("schedulChangeBusStatus->目前时间：" + currTime);
        log.warn("schedulChangeBusStatus->目前时间：" + day);
        System.out.println("目前时间:"+ currTime);
        System.out.println("目前时间:"+ day);
        QueryWrapper<Count> queryWrapper = new QueryWrapper<>();
        // 先取出beingtime和now相等的表或者end_time和now相等到表
        queryWrapper
                .eq("begin_date", day) // 取出当天
                .and(o -> o.eq("begin_time", currTime) // 当前时间
                        .or()
                        .eq("end_time", currTime));
        List<Count> counts = countMapper.selectList(queryWrapper);
        log.warn("schedulChangeBusStatus->查询到的：" + counts.toString());
//        System.out.println("查询到的:"+counts.toString());
        // 这里判断counts是否有值或者为空，不为空才遍历
        for (Count count : counts) {
            String busStatus = count.getBusStatus();
            String beginTime = count.getBeginTime();
            String endTime = count.getEndTime();
            if (currTime.equals(beginTime)) {
                if (busStatus.equals("0")) { // 沙河空闲
                    count.setBusStatus("2"); // 沙河->清水河
                }
                if (busStatus.equals("1")) { // 清水河空闲
                    count.setBusStatus("3"); // 清水河->沙河
                }
                count.setSelectedSeats(""); // 清空座位
            }
            if (currTime.equals(endTime)) {
                if (busStatus.equals("2")) { // 沙河->清水河
                    count.setBusStatus("1"); // 清水河空闲
                }
                if (busStatus.equals("3")) { // 清水河->沙河
                    count.setBusStatus("0"); // 沙河空闲
                }
            }
            System.out.println("修改的：" + count);
            log.warn("schedulChangeBusStatus->修改的：" + count);
            // 写入数据库
            countMapper.updateById(count);
        }
        // 优化的地方，可以调用场次列表服务，查询即可获取总页数，依次判断全部删除
        // 前端默认的请求每页个数是5，因此可以求出......
        // 还有一种方案
        // 不经过数据库获取总页数，用redis
        // 删缓存
//        String key1 = RedisConstants.COUNTS_EXPIRE + "01";
//        String key3 = RedisConstants.COUNTS_EXPIRE + "02";
//        String key2 = RedisConstants.COUNTS_EXPIRE + "11";
//        String key4 = RedisConstants.COUNTS_EXPIRE + "12";
//        if (redisUtils.hasKey(key1)) {
//            redisUtils.del(key1);
//        }
//        if (redisUtils.hasKey(key2)) {
//            redisUtils.del(key2);
//        }
//        if (redisUtils.hasKey(key3)) {
//            redisUtils.del(key2);
//        }
//        if (redisUtils.hasKey(key4)) {
//            redisUtils.del(key2);
//        }
        // 同理，counts有值，才去走以下，  这里为就不判断了。。
        // 由于定时添加的时候， 缓存已经在了。
        // 1、获取key
        String countZeroKey = RedisConstants.COUNTS_PAGES_EXPIRE.getKey() + "0";
        String countOneKey = RedisConstants.COUNTS_PAGES_EXPIRE.getKey() + "1";
        // 2、不判断了，肯定存在，获取value
        Long countZeroPages = Convert.toLong(redisUtils.get(countZeroKey));
        Long countOnePages = Convert.toLong(redisUtils.get(countOneKey));
        // 3、遍历删除
        for (int i = 1; i < countZeroPages; i++) {
            String key = RedisConstants.COUNTS_EXPIRE + "0" + Convert.toStr(i);
            redisUtils.del(key);
        }
        for (int i = 1; i < countOnePages; i++) {
            String key = RedisConstants.COUNTS_EXPIRE + "1" + Convert.toStr(i);
            redisUtils.del(key);
        }
        // 4. 更新场次列表的页数，但是， 这里不更新， 因为在接口那边会判断
    }

    @Override
    public void addCounts() {
        // 获取日期
        String day = DateUtil.getDay();
        // 获取前17个场次
        QueryWrapper<Count> wrapper = new QueryWrapper<>();
        wrapper.last("limit 17");
        List<Count> counts = countMapper.selectList(wrapper);
        // 开始修改 这里可以用java8 的特性， 还不是很熟悉，后期优化一下
        for (Count count : counts) {
            // 更改日期
            count.setBeginDate(day);
            // 更改uuid
            count.setUuid(UUIDUtils.flakesUUID());
            // 清空座位
            count.setSelectedSeats("");
            // 将走位状态清零
            count.setSeatStatus("0");
            // 插入
            countMapper.insert(count);
        }
        // 另一种方案
        // 1、添加完之后，调用场次列表查看总页数
        PageCountRequest request = new PageCountRequest();
        request.setBusStatus("0"); // 沙河
        request.setCurrentPage(Convert.toLong(1));
        request.setPageSize(Convert.toLong(5));
        PageCountResponse countResponse = this.getCount(request);
        Long countPagesZero = countResponse.getPages();
        log.warn("沙河页数：" + countResponse.getPages());
        request.setBusStatus("1"); // 清水河
        PageCountResponse countResponse1 = this.getCount(request);
        Long countPagesOne = countResponse1.getPageSize();
        // 2、构建key
        String countPagesZeroKey = RedisConstants.COUNTS_PAGES_EXPIRE.getKey() + "0";
        String countPagesOneKey = RedisConstants.COUNTS_PAGES_EXPIRE.getKey() + "1";
        // 3、不用判断， 直接覆盖
        redisUtils.set(countPagesZeroKey, countPagesZero);
        redisUtils.set(countPagesOneKey, countPagesOne);
    }

    @Override
    public void scheduledTest() {
        log.warn("test定时器");
    }
}

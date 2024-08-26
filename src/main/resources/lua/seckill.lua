-- 判断库存和一人一单
-- 1 参数
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
-- 2 key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
-- 3 业务
-- 3.1 判断库存
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1  -- 库存不足,返回1
end
-- 3.2 判断用户是否下单过
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2 -- 重复下单,返回2
end
-- 3.3 扣库存(redis)
redis.call('incrby', stockKey, -1)
-- 3.4 下单,记录用户到set
redis.add('sadd', orderKey, userId)
-- 3.5 发送消息到队列 xadd stream.orders * k1 v1 k2 v2
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0
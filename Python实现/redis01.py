# 作业一
import aioredis
import asyncio
loop = asyncio.get_event_loop()

async def main():
    host = "localhost"
    sentinel = await aioredis.create_sentinel(
        [(host, 26379), (host, 26380), (host, 26381)]
    )
    redis = sentinel.master_for("mymaster")
    data = await redis.get("dpf")
    anime = Entity("anime", ["title", "context"], redis)
    # anime => anime:, anime:1 {title: aa, context:aaa}, anime:index: [anime:1]
    await anime.add({"title": "hello world", "context":"kirito"})
    # await anime.delete("1")
    data = await anime.query()
    print(data)

# 定义实体类
class Entity:
    def __init__ (self, key:str, attr:list, redis):
        self.__key = key
        self.__attr = attr
        self.__redis = redis

    def hasAttr(self, value:dict):
        attr = self.__attr
        for k in attr:
            if value.get(k, None) is None: 
                return False
        return True   

    async def add (self, value:dict):
        if not self.hasAttr(value): raise Exception()
        redis = self.__redis
        pipe = redis.pipeline()
        key = self.__key
        id = await redis.incr(key + ":")
        pipe.sadd(key + ":index", key + ":" + str(id))
        pipe.hmset_dict(key + ":" + str(id), value)
        return await pipe.execute()
    
    async def query (self, id = False):
        redis = self.__redis
        key = self.__key
        if id:
            if await redis.exists(key + ":" + str(id)):
              return await redis.hgetall(key + ":" + (id))
        else:
            futs = []
            print(await redis.get(key + ":"))
            s = await redis.smembers(key+":index")
            for k in s:
                futs.append(redis.hgetall(k))
            return await asyncio.gather(*futs)


    async def delete (self, id:str):
        redis = self.__redis
        key = self.__key
        await redis.delete(key + ":" + id)
        await redis.srem(key + ":index",key + ":" + id)
    

    async def update (self, id:str, value:dict):
        redis = self.__redis
        key = self.__key
        if await redis.exists(key + ":" + id):
            futs = []
            for k, v in value.items():
                futs.append(redis.hset(key + ":" + id, k, v))
        asyncio.gather(*futs)


if __name__ == "__main__":
    loop.run_until_complete(main())


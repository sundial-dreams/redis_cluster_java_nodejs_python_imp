// 作业一
const Redis = require("ioredis");
const Entity = require("./util/entity");
const host = "localhost";
// sentinel node
const sentinels = new Redis({
    sentinels: [{ host, port: 26379 }, { host, port: 26380 }, { host, port: 26381 }],
    name: "mymaster",
    connectTimeout: 5000
});

async function main () {
    const article = new Entity(sentinels, "article", ["title", "context"]);
    await article.add({title: "javascript async/await", context: "async function() {}"});
    let data = await article.query();
    console.log(data);
}
main();

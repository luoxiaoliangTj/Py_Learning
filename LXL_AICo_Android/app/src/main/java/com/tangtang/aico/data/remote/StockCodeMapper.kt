package com.tangtang.aico.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 股票名称 → 代码映射器
 * 解决 .md 持仓文件没有股票代码列时，无法通过名称查到代码的问题
 */
@Singleton
class StockCodeMapper @Inject constructor() {

    companion object {
        private const val TAG = "StockCodeMapper"
        private const val CACHE_FILE = "stock_name_code_cache.json"
    }

    private val gson = Gson()
    private var nameToCode: Map<String, String> = emptyMap()
    private var initialized = false

    suspend fun init(context: Context) {
        if (initialized) return
        withContext(Dispatchers.IO) {
            nameToCode = buildBuiltinMap()
            val cacheFile = File(context.filesDir, CACHE_FILE)
            if (cacheFile.exists()) {
                try {
                    val json = cacheFile.readText()
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val cached: Map<String, String> = gson.fromJson(json, type) ?: emptyMap()
                    if (cached.isNotEmpty()) nameToCode = nameToCode + cached
                } catch (e: Exception) { Log.w(TAG, "加载缓存失败: ${e.message}") }
            }
            Log.d(TAG, "StockCodeMapper 初始化完成: ${nameToCode.size} 条")
            initialized = true
        }
    }

    fun getCodeByName(name: String): String? {
        if (!initialized) Log.w(TAG, "StockCodeMapper 未初始化")
        nameToCode[name]?.let { return it }
        val cleaned = name.replace(Regex("[（(].*?[)）]"), "").trim()
        if (cleaned != name) nameToCode[cleaned]?.let { return it }
        return null
    }

    suspend fun putMapping(name: String, code: String, context: Context) {
        nameToCode = nameToCode + (name to code)
        withContext(Dispatchers.IO) {
            try { File(context.filesDir, CACHE_FILE).writeText(gson.toJson(nameToCode)) }
            catch (e: Exception) { Log.w(TAG, "保存缓存失败: ${e.message}") }
        }
    }

    private fun buildBuiltinMap(): Map<String, String> = mapOf(
        // 你持仓文件中的股票
        "农业银行" to "601288", "荣盛发展" to "002146", "中国平安" to "601318",
        "海油发展" to "600968", "紫光国微" to "002049", "上港集团" to "600018",
        "天津港" to "600717", "万科A" to "000002", "北自科技" to "603082",
        "海航控股" to "600221", "日本东证指数ETF南" to "513800",
        "北京银行" to "601169", "国元证券" to "000728", "中联重科" to "000157",
        "四方达" to "300179", "迎驾贡酒" to "603198", "建设银行" to "601939",
        "海康威视" to "002415", "骑士乳业" to "688273", "哈药股份" to "600664",
        "中航西飞" to "000768", "电投能源" to "002128", "致欧科技" to "301376",
        "格力博" to "301260", "金桥信息" to "603918", "上海电气" to "601727",
        "电投绿能" to "600795", "晶瑞电材" to "300655", "中钢国际" to "000928",
        "蓝箭电子" to "301387", "中国东航" to "600115",
        "标普500ETF博时" to "513500", "河钢股份" to "000709",
        "香港证券ETF易方达" to "513090",
        // 常用补充
        "贵州茅台" to "600519", "宁德时代" to "300750", "比亚迪" to "002594",
        "招商银行" to "600036", "工商银行" to "601398", "中国银行" to "601988",
        "交通银行" to "601328", "邮储银行" to "601658", "兴业银行" to "601166",
        "浦发银行" to "600000", "中信证券" to "600030", "东方财富" to "300059",
        "恒瑞医药" to "600276", "药明康德" to "603259", "迈瑞医疗" to "300760",
        "中国中免" to "601888", "隆基绿能" to "601012", "通威股份" to "600438",
        "阳光电源" to "300274", "万华化学" to "600309", "紫金矿业" to "601899",
        "长江电力" to "600900", "中国神华" to "601088", "中国石油" to "601857",
        "中国石化" to "600028", "中国海油" to "600938", "中远海控" to "601919",
        "顺丰控股" to "002352", "立讯精密" to "002475", "京东方A" to "000725",
        "TCL科技" to "000100", "格力电器" to "000651", "美的集团" to "000333",
        "海尔智家" to "600690", "三一重工" to "600031", "中联重科" to "000157",
        "海螺水泥" to "600585", "宝钢股份" to "600019", "华菱钢铁" to "000932",
        "北方稀土" to "600111", "赣锋锂业" to "002460", "天齐锂业" to "002466",
        "华友钴业" to "603799", "洛阳钼业" to "603993", "中国铝业" to "601600",
        "云铝股份" to "000807", "南山铝业" to "600219", "神火股份" to "000933",
        "中煤能源" to "601898", "陕西煤业" to "601225", "兖矿能源" to "600188",
        "华能国际" to "600011", "大唐发电" to "601991", "国电电力" to "600795",
        "华电国际" to "600027", "国投电力" to "600886", "三峡能源" to "600905",
        "中国核电" to "601985", "中国广核" to "003816", "上汽集团" to "600104",
        "广汽集团" to "601238", "长城汽车" to "601633", "长安汽车" to "000625",
        "小康股份" to "601127", "北汽蓝谷" to "600733", "江淮汽车" to "600418",
        "宇通客车" to "600066", "金龙汽车" to "600686", "比亚迪" to "002594",
        "潍柴动力" to "000338", "一汽解放" to "000800", "东风汽车" to "600006",
        "江铃汽车" to "000550", "中集集团" to "000039", "中国重汽" to "000951",
        "徐工机械" to "000425", "柳工" to "000528", "山推股份" to "000680",
        "厦工股份" to "600815", "安徽合力" to "600761", "杭叉集团" to "601882",
        "浙江鼎力" to "603338", "中联重科" to "000157",
        // 主要ETF
        "沪深300ETF" to "510300", "中证500ETF" to "510500",
        "创业板ETF" to "159915", "科创50ETF" to "588000",
        "证券ETF" to "512880", "银行ETF" to "512800",
        "医药ETF" to "512010", "消费ETF" to "510150",
        "军工ETF" to "512660", "半导体ETF" to "512480",
        "新能源ETF" to "516160", "光伏ETF" to "515790",
        "红利ETF" to "510880", "国债ETF" to "511010",
        "黄金ETF" to "518880", "恒生ETF" to "159920",
        "恒生科技ETF" to "513180", "中概互联网ETF" to "513050",
        "纳指ETF" to "513100", "标普500ETF" to "513500",
        "日经225ETF" to "513800", "德国30ETF" to "513030",
        "法国CAC40ETF" to "513080", "印度ETF" to "164824",
        "越南ETF" to "008763", "香港证券ETF" to "513090",
        "东证ETF" to "513800",
        // 主要指数
        "上证指数" to "000001", "深证成指" to "399001", "创业板指" to "399006",
        "沪深300" to "000300", "中证500" to "000905", "科创50" to "000688",
    )
}

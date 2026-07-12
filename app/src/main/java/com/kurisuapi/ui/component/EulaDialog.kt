package com.kurisuapi.ui.component

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kurisuapi.util.sdp

/**
 * 用户协议与隐私政策弹窗。
 *
 * 首次启动时强制弹出，用户必须点击【同意】才能进入 App。
 * 点击【不同意并退出】会直接退出 App。
 *
 * 法律依据：
 * - 《中华人民共和国民法典》（合同编）
 * - 《中华人民共和国个人信息保护法》（2021年11月1日施行）
 * - 《中华人民共和国网络安全法》（2017年6月1日施行）
 * - 《中华人民共和国数据安全法》（2021年9月1日施行）
 * - 《生成式人工智能服务管理暂行办法》（2023年8月15日施行）
 * - 《互联网信息服务深度合成管理规定》（2023年1月10日施行）
 * - 《中华人民共和国未成年人保护法》（2021年修订）
 * - 《网络数据安全管理条例》（2025年1月1日施行）
 */
@Composable
fun EulaDialog(
    onAccept: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* 禁止点外部关闭，强制用户选择 */ },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("用户协议与隐私政策", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ═══════════════ 前言 ═══════════════
                Text(
                    "更新日期：2026年6月27日\n生效日期：2026年6月27日",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "请您在使用 KurisuAPI（以下简称【本软件】）之前，仔细阅读并充分理解本协议各条款的全部内容，特别是以加粗、下划线等方式提示您注意的、免除或限制责任的条款。如您不同意本协议的任何条款，请立即停止下载、安装或使用本软件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ═══════════════ 一、定义 ═══════════════
                Text("第一条 定义与适用范围", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "1.1 【开发者】（【我们】）指本软件的开发者，即北原友希（Yuki Kitahara）。\n" +
                    "1.2 【用户】（【您】）指下载、安装、使用本软件的自然人。\n" +
                    "1.3 【本软件】指 KurisuAPI 移动应用程序及其相关组件、代码和文档。\n" +
                    "1.4 【AI服务商】指用户自行选择接入的第三方人工智能大模型服务提供商，包括但不限于 OpenAI、Anthropic、Google（Gemini）、DeepSeek 及其他 OpenAI 兼容接口的服务商。\n" +
                    "1.5 【个人信息】指以电子或其他方式记录的、能够单独或与其他信息结合识别特定自然人的各种信息，包括但不限于用户的 API Key、聊天记录、角色设定等。\n" +
                    "1.6 【AI生成内容】指通过用户接入的第三方 AI 服务商的接口，由大语言模型自动生成的文字、图像、音频、视频或其他形式的输出内容。\n" +
                    "1.7 本协议适用于您下载、安装、注册、使用本软件的全部行为。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 二、软件使用许可 ═══════════════
                Text("第二条 软件使用许可", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "2.1 开发者授予您一项非排他性、不可转让、不可再许可的有限使用权，您可在中国大陆地区（不含港澳台）将本软件安装于您个人所有的移动终端设备上，用于个人学习、研究和娱乐目的。\n" +
                    "2.2 您不得将本软件用于任何商业用途，包括但不限于向第三方提供有偿服务、将本软件嵌入商业产品、利用本软件开展经营性活动等。\n" +
                    "2.3 本软件为开源项目，源代码基于其附带的开源许可证发布。您可依据该开源许可证的条款自由使用、复制、修改和分发本软件的源代码。但本协议中的免责条款、责任限制条款不受开源许可证的影响。\n" +
                    "2.4 本软件当前为免费软件，开发者保留未来调整功能或模式的权利。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 三、用户义务与禁止行为 ═══════════════
                Text("第三条 用户义务与禁止行为", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "3.1 您在使用本软件时应严格遵守中华人民共和国法律法规，不得从事以下行为：\n" +
                    "（1）利用本软件或AI生成内容制作、复制、发布、传播危害国家安全、破坏国家统一、损害国家荣誉和利益、煽动民族仇恨、破坏民族团结、宣扬邪教迷信的内容；\n" +
                    "（2）利用本软件或AI生成内容制作、复制、发布、传播淫秽、色情、赌博、暴力、凶杀、恐怖或者教唆犯罪的内容；\n" +
                    "（3）利用本软件进行侮辱、诽谤、人肉搜索、侵犯他人名誉权、隐私权、肖像权、知识产权或其他合法权益的行为；\n" +
                    "（4）利用本软件进行网络诈骗、虚假宣传、传销、非法集资等违法犯罪活动；\n" +
                    "（5）利用本软件对他人进行骚扰、跟踪、威胁或任何形式的恶意行为；\n" +
                    "（6）利用本软件接入的AI服务生成虚假信息、误导性内容并对外传播，造成社会恐慌或不良影响；\n" +
                    "（7）利用本软件的微信机器人功能发送垃圾信息、营销广告、恶意程序或进行其他违反《微信个人账号使用规范》的行为；\n" +
                    "（8）以任何方式试图破解、逆向工程、反编译本软件，或未经授权访问本软件的本地数据库文件；\n" +
                    "（9）其他违反法律法规、社会公德或公序良俗的行为。\n" +
                    "3.2 您同意对自己使用本软件的行为及其产生的后果承担全部法律责任。如您发现任何违法或不当内容，应立即停止使用并及时向开发者反馈。\n" +
                    "3.3 开发者保留在发现用户违反上述规定时，通过技术手段限制相关功能的权利。",

                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 四、隐私与数据保护 ═══════════════
                Text("第四条 隐私与数据保护", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "4.1 数据本地化原则：本软件的核心设计原则是【数据完全归属于您】。您在软件中创建的所有内容——包括但不限于角色设定（角色名称、性格描述、系统提示词等）、与AI的聊天记录、记忆数据、情绪状态数据、关系数据、个人资料设置、日记内容——均仅存储于您的移动终端设备的本地数据库中。开发者不会、也无法以任何方式访问、收集、查看、上传、备份或传输您的上述数据。\n" +
                    "4.2 API Key 的加密保护：您在本软件中填写的第三方 AI 服务商的 API Key（应用程序接口密钥），将存储于您的设备操作系统提供的加密安全存储区域（Android EncryptedSharedPreferences），需通过系统级身份验证方可读取。该密钥仅由本软件在运行过程中调用您指定的 AI 服务商接口时使用，不会以明文形式写入普通日志、崩溃报告或任何可被其他应用程序读取的位置。开发者无法在技术上获取您存储在加密安全存储中的 API Key。\n" +
                    "4.3 无服务器架构：本软件不依赖开发者运营的任何后端服务器。您的数据不会上传至任何由开发者控制的服务器、云存储或第三方数据分析平台。本软件不嵌入、不使用任何形式的第三方数据分析 SDK（如友盟、百度统计等）、广告投放 SDK、用户行为追踪 SDK、崩溃统计 SDK 或热修复框架。本软件不会以任何形式追踪、记录、分析或上报您的使用行为、操作习惯、使用时长、页面停留时间等信息。\n" +
                    "4.4 网络通信：本软件仅在以下场景进行网络通信：（1）调用您自行填写的第三方 AI 服务商 API 接口发送聊天请求并接收回复；（2）您主动启用的微信机器人功能涉及的微信API通信；（3）您主动触发的版本更新检查。上述通信中传输的数据仅为您主动发送的请求内容及必要的接口鉴权凭证，开发者无法截获、记录或查看。\n" +
                    "4.5 数据删除与卸载：您可随时在本软件内删除任何数据（单条聊天记录、单条记忆、整个对话会话、角色等）。卸载本软件将永久清除存储在您设备上的全部数据，且不可恢复。建议您在卸载前自行备份重要数据。本软件目前不提供云端备份功能。\n" +
                    "4.6 您的权利：依据《个人信息保护法》，您对本软件中存储的您的个人信息享有以下权利——查阅权（在App内即可查看所有数据）、更正权（在App内编辑修改）、删除权（在App内删除或在系统设置中清除应用数据）、撤回同意权（通过卸载本软件行使）。您无需注册账号即可行使上述权利。\n" +
                    "4.7 本软件不收集、不存储、不利用任何形式的生物识别信息（人脸、指纹、声纹）、精确位置信息、通讯录信息、短信记录、通话记录、相册内容（您主动选择导入的图片除外，该图片仅存储于本地）、设备应用列表或其他敏感个人信息。",

                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 五、AI生成内容与责任豁免 ═══════════════
                Text("第五条 AI生成内容与责任豁免", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "5.1 软件性质声明：本软件是一款【AI角色陪伴客户端工具】。本软件自身不具备人工智能生成能力，其作用是为用户提供一个界面（UI）和业务逻辑层，以连接用户指定并授权的第三方 AI 服务商的应用程序接口（API）。AI 回复内容完全由用户选择接入的第三方大语言模型生成。\n" +
                    "5.2 AI内容的不可控性：AI 大语言模型的输出具有内在的随机性、不确定性和潜在的错误性。AI 可能生成不准确、不完整、有误导性、有偏见、不适当、不符合事实的内容，甚至在特定情况下可能生成包含色情、暴力、血腥、政治敏感等违法或不良信息。这是当前大语言模型技术的内在特征和固有局限，非开发者所能预见、控制、过滤或消除。开发者作为软件工具的技术提供者，没有能力也没有义务对您与第三方AI服务商之间传输的对话内容进行实时监控、审查或过滤。\n" +
                    "5.3 免责声明（重要）：在适用法律允许的最大范围内，开发者明确声明——\n" +
                    "（1）不保证AI生成内容的合法性：开发者不对任何 AI 生成内容的合法性、真实性、准确性、完整性、可靠性、安全性、适当性作任何形式的明示或默示的保证。AI 可能因模型训练数据、用户输入引导等多种因素，生成包含色情、暴力、血腥、政治敏感、侮辱诽谤、歧视仇恨或其他违反中华人民共和国法律法规及公序良俗的内容。开发者对此类内容的产生不负任何责任。\n" +
                    "（2）违法内容与开发者无关：若您通过本软件接入的第三方 AI 服务生成了任何违法犯罪内容（包括但不限于色情淫秽、暴力恐怖、血腥残忍、危害国家安全、破坏民族团结、侮辱诽谤他人、侵犯他人合法权益以及其他违反《中华人民共和国刑法》《中华人民共和国治安管理处罚法》《中华人民共和国网络安全法》《生成式人工智能服务管理暂行办法》等法律法规的内容），该等内容的生成和传播行为完全由您自行负责，与开发者无关。开发者不承担由此产生的任何直接、间接、连带或替代责任。您将独自承担因上述行为引发的全部法律责任、行政责任和民事赔偿责任。\n" +
                    "（3）不承担AI内容责任：AI 生成内容不代表开发者的观点、态度或立场。因AI生成内容导致的任何争议、纠纷、损害（包括但不限于名誉损害、精神损害、财产损失、数据损失、法律纠纷、刑事追诉等），由您自行承担，开发者不承担任何责任。\n" +
                    "（4）用户自行判断：您应当独立判断 AI 生成内容的可靠性和适用性，不应当将 AI 生成内容作为专业建议（包括但不限于医疗建议、法律建议、投资建议、心理咨询等）。如您需要上述专业建议，请咨询具备相应资质的专业人士。\n" +
                    "（5）第三方服务商责任：如 AI 生成内容侵犯了他人的知识产权、名誉权、隐私权等合法权益，您应直接向生成该内容的第三方 AI 服务商主张权利，开发者不承担连带责任。\n" +
                    "5.4 深度合成标识义务：根据《互联网信息服务深度合成管理规定》及《生成式人工智能服务管理暂行办法》，您在使用 AI 生成图片、视频等内容并对外公开发布时，有义务对该等内容进行显著标识，告知公众该内容系由AI生成。您应自行承担未标识或未充分标识所产生的法律责任。\n" +
                    "5.5 开发者不是【生成式AI服务提供者】：根据《生成式人工智能服务管理暂行办法》第二条及第二十二条，【生成式AI服务提供者】指利用生成式AI技术向中华人民共和国境内公众提供服务的组织或个人。本软件的开发者北原友希仅为软件工具的技术提供者，不向用户提供生成式AI服务——AI生成能力由用户自行选择接入的第三方服务商提供。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 六、第三方服务 ═══════════════
                Text("第六条 第三方服务", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "6.1 第三方AI服务商：本软件支持用户自行配置并接入第三方AI服务商。您与第三方AI服务商之间的关系是独立的、直接的、仅存在于您与该服务商之间的法律关系。开发者既非您的代理人，亦非该服务商的代理人，不参与、不介入、不对该法律关系的成立、履行、变更和终止承担任何义务或责任。\n" +
                    "6.2 API使用费用：您使用第三方AI服务商API所产生的全部费用（包括但不限于API调用费、订阅费、超额使用费等），由您自行向对应的服务商支付并承担。本软件不收取任何费用，亦不参与您的费用结算。\n" +
                    "6.3 API可用性与服务中断：第三方AI服务商的API可能出现不可用、响应延迟、服务质量下降、服务终止、接口变更、价格调整等情况。上述情况均不在开发者的控制范围之内，开发者不对由此导致的任何不便或损失承担责任。\n" +
                    "6.4 API服务协议：您使用第三方AI服务商的行为，须同时遵守您与该服务商之间签订的用户协议、隐私政策及其他相关法律文件。您应自行了解并遵守这些文件的规定。如您违反了第三方服务商的服务条款，由此产生的后果（包括但不限于账户被封禁、余额被扣除、法律追诉等）由您自行承担。\n" +
                    "6.5 微信功能合规声明：本软件的微信机器人功能允许您将本软件与您个人注册的微信账号绑定，实现自动回复功能。您明确知悉并同意——（1）您用于绑定的微信账号为您个人所有，您应遵守腾讯公司的《微信软件许可及服务协议》和《微信个人账号使用规范》；（2）使用自动化工具操作微信账号可能存在违反腾讯相关协议的风险，包括但不限于微信账号被限制功能或永久封禁，开发者不对上述风险导致的任何损失承担责任；（3）您不得利用微信机器人功能发送垃圾信息、骚扰他人、从事营销推广或实施任何违法行为；（4）微信机器人功能仅作为本软件的一项可选便利功能，不是核心功能，您可随时选择不使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 七、知识产权 ═══════════════
                Text("第七条 知识产权", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "7.1 本软件的著作权：本软件的源代码、程序架构、UI设计、相关文档的知识产权归开发者所有（或已依据开源许可证从第三方合法获取授权）。本软件的源代码基于 GNU General Public License v3.0（GPL 3.0）发布，您可在遵守该许可证条款的前提下自由使用、修改和分发本软件的源代码。GPL 3.0 为 copyleft 许可证，要求任何基于本软件源代码的修改版本在分发时也必须以 GPL 3.0 开源。\n" +
                    "7.2 用户创作内容：您利用本软件输入的提示词（Prompt）、创建的角色设定、与AI协作生成的对话内容以及其他由您创作产生的内容，其著作权和其他相关权利归属于您本人。开发者不对您创作的内容主张任何权利。\n" +
                    "7.3 AI生成内容的权属：关于AI生成内容的著作权归属，目前在世界范围内尚无统一的法律规定和司法实践。您应自行了解并承担AI生成内容在著作权归属方面可能存在的法律不确定性和风险。\n" +
                    "7.4 第三方权利：本软件中可能包含、引用或调用第三方的软件库、API、字体、图标、商标、服务标志等，该等第三方的知识产权归各自权利人所有。您对本软件的使用不构成对该等第三方知识产权的任何授权或许可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 八、责任限制 ═══════════════
                Text("第八条 责任限制与免责声明", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "8.1 本软件按【现状】（AS IS）提供。在法律允许的最大范围内，开发者明确声明不提供任何形式的明示或默示保证，包括但不限于对适销性、特定用途适用性、不侵权、无病毒/恶意代码、数据安全的保证。\n" +
                    "8.2 开发者不保证本软件的功能完全满足您的要求，不保证本软件的运行完全不受干扰或没有错误，不保证本软件中的所有缺陷均会被修正。\n" +
                    "8.3 在任何情况下，开发者均不对因使用或无法使用本软件而引起的任何间接的、附带的、特殊的、惩罚性的或后果性的损害赔偿（包括但不限于数据丢失、业务中断、利润损失、商誉损失、设备损坏、精神损害等）承担责任，即使开发者已被告知此类损害的可能性。\n" +
                    "8.4 在适用法律不允许排除或限制特定类型责任的情况下，上述责任限制中的相应条款不予适用。开发者的累计赔偿责任总额，在任何情况下均不超过您在过去十二（12）个月内因使用本软件而向开发者支付的费用总额（如有）。鉴于本软件当前为免费软件，该总额为零（0元人民币）。\n" +
                    "8.5 上述免责声明和责任限制条款是本协议的基础条款，反映了双方在协商本协议时的风险分配。开发者依赖本条款向您免费提供本软件。如本条款的任何部分被认定为不可执行，其余部分仍保持完全效力。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 九、赔偿 ═══════════════
                Text("第九条 赔偿", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "9.1 您同意赔偿并使开发者免受以下原因引起的任何索赔、诉讼、损害、损失、费用（包括合理的律师费、诉讼费、差旅费等）：（1）您违反本协议的任何条款；（2）您违反任何适用法律或第三方权利；（3）您利用AI生成内容对他人造成损害，而该行为被认定为开发者未尽到合理监管义务（尽管开发者的立场是本软件不构成【生成式AI服务提供】）。\n" +
                    "9.2 开发者保留对本协议约定赔偿事项之外的、因您的原因给开发者造成损失的事项进行独立追偿的权利。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 十、未成年人保护 ═══════════════
                Text("第十条 未成年人保护", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "10.1 本软件并非面向未成年人设计，也不以未成年人为目标用户群体。本软件没有收集用户年龄信息的机制，因此无法主动识别用户是否为未成年人。\n" +
                    "10.2 如果您是未成年人（即未满18周岁的自然人），您应当在您的父母或其他法定监护人的充分知情、明确同意和全程监督下使用本软件。\n" +
                    "10.3 未成年人的监护人应仔细阅读本协议，充分了解AI工具可能输出不适宜内容的风险，指导未成年人合理控制使用时长，避免过度情感依赖AI角色，并警惕AI对话可能对未成年人心理健康产生的影响。\n" +
                    "10.4 如您是未成年人的监护人，且发现被监护人在未经您同意的情况下使用本软件或通过本软件接触了不适宜的内容，您有权指导被监护人删除相关数据。相关操作路径为：在本App内对应的功能页面进行删除，或卸载本软件以清除全部数据。\n" +
                    "10.5 开发者在知悉某用户为未满14周岁的儿童的情况下，将不会与该用户建立任何形式的法律关系，并将采取措施删除其在设备本地存储的所有数据（需用户配合）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 十一、协议终止 ═══════════════
                Text("第十一条 协议终止", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "11.1 本协议自您点击【同意】按钮之时起生效，直至根据本条款终止。\n" +
                    "11.2 您可随时通过卸载本软件的方式终止本协议。卸载后，您的设备上存储的全部数据将被永久删除。\n" +
                    "11.3 如您严重违反本协议的任何条款，开发者有权通过技术手段限制您对相关功能的使用。\n" +
                    "11.4 第3条（用户义务）、第4条（隐私与数据保护）、第5条（AI生成内容与责任豁免）、第6条（第三方服务）、第7条（知识产权）、第8条（责任限制）、第9条（赔偿）以及本第11条，在本协议终止后仍然有效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 十二、争议解决 ═══════════════
                Text("第十二条 适用法律与争议解决", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "12.1 本协议的订立、生效、解释、履行、变更、解除、终止以及由此产生的任何争议的解决，均适用中华人民共和国的法律（为本协议之目的，不包括香港特别行政区、澳门特别行政区和台湾地区的法律）。\n" +
                    "12.2 您和开发者应首先通过友好协商的方式解决因本协议引起的或与本协议有关的任何争议。协商不成的，任何一方均有权向开发者所在地有管辖权的人民法院提起诉讼。\n" +
                    "12.3 本条争议解决条款在本协议终止后仍然有效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // ═══════════════ 十三、其他 ═══════════════
                Text("第十三条 其他条款", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "13.1 完整性：本协议构成您与开发者之间关于本软件使用的完整协议，取代双方之前就同一主题达成的任何口头或书面的沟通、声明、承诺和协议。\n" +
                    "13.2 可分割性：如本协议中的任何条款被有管辖权的法院或其他裁判机构认定为无效、违法或不可执行，该条款应被视为可从本协议中分割，其余条款的效力不受影响，仍应保持完全有效并继续执行。双方应本着诚信原则，以合法有效且最能反映该无效条款商业意图的新条款替换之。\n" +
                    "13.3 不弃权：开发者未行使或延迟行使本协议项下的任何权利、权力或救济措施，不构成对该等权利、权力或救济措施的放弃。单独或部分行使本协议项下的任何权利、权力或救济措施，不妨碍后续行使或进一步行使该等或其他权利、权力或救济措施。\n" +
                    "13.4 协议更新：开发者保留在必要时更新本协议的权利。本协议发生重大变更时，开发者将通过App弹窗方式重新向您征求同意。您在协议更新后继续使用本软件，即视为您已阅读并同意更新后的协议。如您不同意更新后的协议，您应停止使用本软件并卸载。\n" +
                    "13.5 通知与联系：如您对本协议有任何疑问，或需要就与本软件相关的任何事项联系开发者，您可通过以下方式联系：开发者的GitHub主页（https://github.com/Kitahara-Yuki），或开发者的抖音账号（主页链接：https://v.douyin.com/cElW_fMja-0/）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "请您确认已完整阅读、充分理解并自愿接受本协议的全部条款。\n\n" +
                    "点击【同意】按钮，即表示您已年满18周岁（或已获得监护人的明确同意），且已阅读并同意受本《用户协议与隐私政策》全部条款的约束。\n\n" +
                    "点击【不同意并退出】按钮，本软件将立即关闭，不会在您的设备上保留任何数据。",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("同意")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                (context as? Activity)?.finishAffinity()
                onExit()
            }) {
                Text("不同意并退出", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

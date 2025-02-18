# 和自己喜欢的角色聊天

本项目使用的是硅基流动平台提供的api，仅供学习交流使用。

# 基本使用方法：
1.在硅基流动平台上复制下自己的api-key,然后将该api-key填入到配置文件config.properties文件中的voice.api.key=中。

2.硅基流动平台的文档中心->API手册->上传参考音频->try it中，上传角色的参考音频，
然后将返回结果中的url填入到项目中的config.properties文件中,最后修改promoet中的内容。需要注意的是，在上传参考音频时，customName一栏中，不能使用中文。

3.点击项目目录下的 如果更改了配置，请点击此处重新打包.bat 文件，完成打包操作。（如后续使用时未更改配置文件，则不需要此操作）

4.点击项目目录下的 点击我运行.bat 文件 运行程序。

# 注意事项

1.为了更快的响应速度，本项目文本对话使用的模型是pro-r1,该模型不能使用免费额度，需要用户充值少许金额。

2.配置文件config.properties中有许多自定义操作，都有注释可以自行研究更改。

3.运行程序前需要提前安装好jdk和maven。

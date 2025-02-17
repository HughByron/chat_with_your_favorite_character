# chat_with_your_favorite_character
和自己喜欢的角色聊天

基本使用方法：
1.在硅基流动平台上复制下自己的api-key,然后将该api-key填入到config.properties文件中的voice.api.key=中。
2.点击运行voiceCustomization.bat文件即可。

注意事项
1.默认角色为心海。如需使用其他角色语音，需要在硅基流动平台的文档中心->API手册->上传参考音频->try it中，上传角色的参考音频，然后将返回结果中的url填入到项目中的config.properties文件中。
需要注意的是，在上传参考音频时，customName一栏中，不能使用中文。
2.为了更快的响应速度，本项目文本对话使用的模型是pro-r1,该模型不能使用免费额度，需要用户充值少许金额。
3.每次更改了配置之后，如果想使用.bat文件直接运行.jar包，而不通过运行idea的方式打开，则需要使用maven指令重新打包。
Kotlin管道Channel+Flow流实现的并发任务优先级调度框架系统。


使用前需要先初始化LoadMgr:
LoadMgr.INSTANCE.starup()

（1）最简单的使用方式：

LoadMgr.INSTANCE.submit{
  //写耗时任务代码逻辑
}

这样就启动了一个默认为NORMAL等级的并行任务。


（2）如果要启动一个高（HIGH）优先级的并行任务，则是：
LoadMgr.INSTANCE.submit(Priority.HIGH){
  //写耗时任务代码逻辑
}


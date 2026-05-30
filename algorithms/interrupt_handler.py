# algorithms/interrupt_handler.py
"""
用户中断处理模块
"""
import threading
import sys
import select
import time

class UserInterruptHandler:
    """用户中断监听器 - 非阻塞方式"""
    
    def __init__(self):
        self._interrupt_requested = False
        self._listener_thread = None
        self._stop_listener = False
        
    def start_listening(self):
        """开始监听用户中断"""
        self._interrupt_requested = False
        self._stop_listener = False
        self._listener_thread = threading.Thread(target=self._input_listener)
        self._listener_thread.daemon = True
        self._listener_thread.start()
        
    def stop_listening(self):
        """停止监听"""
        self._stop_listener = True
        if self._listener_thread and self._listener_thread.is_alive():
            self._listener_thread.join(timeout=1)
            
    def _input_listener(self):
        """输入监听线程"""
        while not self._stop_listener:
            # 非阻塞检查标准输入
            if sys.stdin in select.select([sys.stdin], [], [], 0.1)[0]:
                user_input = sys.stdin.readline().strip().lower()
                if user_input in ['q', 'quit', 'exit', 'stop']:
                    self._interrupt_requested = True
                    break
            time.sleep(0.1)
    
    def check_interrupt(self):
        """检查是否有中断请求"""
        return self._interrupt_requested
    
    def clear_interrupt(self):
        """清除中断状态"""
        self._interrupt_requested = False
import { io } from 'socket.io-client';

let socketInstance = null;

export function getSocket() {
  if (!socketInstance) {
    socketInstance = io('/', {
      withCredentials: true,
      autoConnect: false,
      reconnectionAttempts: 10,
      reconnectionDelay: 1000
    });
  }
  return socketInstance;
}

export function connectSocket() {
  const socket = getSocket();
  if (!socket.connected) {
    socket.connect();
  }
  return socket;
}

export function disconnectSocket() {
  if (socketInstance) {
    socketInstance.disconnect();
    socketInstance = null;
  }
}

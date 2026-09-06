import { Client } from '@stomp/stompjs';
import type { IMessage } from '@stomp/stompjs';

class WebSocketService {
  private client: Client | null = null;
  private isConnected = false;

  public connect(onConnected?: () => void, onError?: (err: any) => void) {
    if (this.client && this.isConnected) {
      if (onConnected) onConnected();
      return;
    }

    const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

    this.client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.isConnected = true;
        if (onConnected) onConnected();
      },
      onStompError: (frame) => {
        console.warn('Avertissement STOMP :', frame);
        if (onError) onError(frame);
      },
      onWebSocketClose: () => {
        this.isConnected = false;
      },
      onWebSocketError: (event) => {
        // Log discret si le backend est encore en cours de démarrage
        console.warn('WebSocket en attente du serveur Spring Boot...', event);
        if (onError) onError(event);
      },
    });

    try {
      this.client.activate();
    } catch (e) {
      console.warn('Erreur activation client STOMP :', e);
    }
  }

  public subscribeToGame(gameId: string, onMessage: (message: any) => void) {
    if (!this.client || !this.isConnected) {
      this.connect(() => {
        this.client?.subscribe(`/topic/game/${gameId}`, (msg: IMessage) => {
          try {
            onMessage(JSON.parse(msg.body));
          } catch (e) {
            console.error('Erreur parsing payload game :', e);
          }
        });
      });
      return;
    }

    return this.client.subscribe(`/topic/game/${gameId}`, (msg: IMessage) => {
      try {
        onMessage(JSON.parse(msg.body));
      } catch (e) {
        console.error('Erreur parsing payload game :', e);
      }
    });
  }

  public subscribeToMatchmaking(userId: string, onMessage: (message: any) => void) {
    if (!this.client || !this.isConnected) {
      this.connect(() => {
        this.client?.subscribe(`/topic/matchmaking/${userId}`, (msg: IMessage) => {
          try {
            onMessage(JSON.parse(msg.body));
          } catch (e) {
            console.error('Erreur parsing payload matchmaking :', e);
          }
        });
      });
      return;
    }

    return this.client.subscribe(`/topic/matchmaking/${userId}`, (msg: IMessage) => {
      try {
        onMessage(JSON.parse(msg.body));
      } catch (e) {
        console.error('Erreur parsing payload matchmaking :', e);
      }
    });
  }

  public sendReady(gameId: string, playerId: string) {
    this.send(`/app/game/${gameId}/ready`, { playerId });
  }

  public sendBuzz(gameId: string, playerId: string) {
    this.send(`/app/game/${gameId}/buzz`, { playerId });
  }

  public sendAnswer(gameId: string, playerId: string, guess: string) {
    this.send(`/app/game/${gameId}/answer`, { playerId, guess });
  }

  public sendBonus(gameId: string, playerId: string, guess: string) {
    this.send(`/app/game/${gameId}/bonus`, { playerId, guess });
  }

  public sendNextRound(gameId: string) {
    this.send(`/app/game/${gameId}/next-round`, {});
  }

  public sendForfeit(gameId: string, playerId: string) {
    this.send(`/app/game/${gameId}/forfeit`, { playerId });
  }

  public sendSkipRound(gameId: string, playerId: string) {
    this.send(`/app/game/${gameId}/skip`, { playerId });
  }

  private send(destination: string, body: any) {
    if (this.client && this.isConnected) {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  }

  public disconnect() {
    if (this.client) {
      try {
        this.client.deactivate();
      } catch (e) {
        // Ignorer à la fermeture
      }
      this.isConnected = false;
    }
  }
}

export const wsService = new WebSocketService();

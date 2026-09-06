import React, { useEffect, useState } from 'react';
import { Navbar } from './components/Navbar';
import { LobbyScreen } from './components/LobbyScreen';
import { VersusArenaScreen } from './components/VersusArenaScreen';
import { ProfileScreen } from './components/ProfileScreen';
import { useGameStore } from './store/useGameStore';
import { useAuthStore } from './store/useAuthStore';
import { wsService } from './services/websocket';
import { authService } from './services/api';

export const App: React.FC = () => {
  const { gameId, phase, onRoundStart, onPlayerBuzzed, onFirstAnswerCorrect, onStealOpen, onRoundEnd, onMatchFinished } = useGameStore();
  const { setUser } = useAuthStore();
  const [currentView, setCurrentView] = useState<'LOBBY' | 'PROFILE'>('LOBBY');

  // Gestion du retour OAuth2 Google (/auth/callback)
  useEffect(() => {
    if (window.location.pathname.includes('/auth/callback')) {
      authService.getMe()
        .then((userData) => {
          setUser(userData);
          window.history.replaceState({}, document.title, '/');
        })
        .catch((err) => {
          console.error("Erreur de récupération du profil Google :", err);
          window.history.replaceState({}, document.title, '/');
        });
    }
  }, [setUser]);

  // Connexion WebSocket STOMP globale au démarrage
  useEffect(() => {
    wsService.connect(
      () => console.log('WebSocket STOMP connecté avec succès'),
      (err) => console.error('Erreur de connexion WebSocket :', err)
    );
    return () => wsService.disconnect();
  }, []);

  // Écoute des événements de partie sur /topic/game/{gameId}
  useEffect(() => {
    if (!gameId) return;

    const sub = wsService.subscribeToGame(gameId, (payload) => {
      switch (payload.event) {
        case 'ROUND_START':
          onRoundStart(payload);
          break;
        case 'PLAYER_BUZZED':
          onPlayerBuzzed(payload);
          break;
        case 'FIRST_ANSWER_CORRECT':
          onFirstAnswerCorrect(payload);
          break;
        case 'ANSWER_FAILED_STEAL_OPEN':
          onStealOpen(payload);
          break;
        case 'ROUND_END':
          onRoundEnd(payload);
          break;
        case 'MATCH_FINISHED':
        case 'SOLO_MATCH_FINISHED':
          onMatchFinished(payload);
          break;
        default:
          break;
      }
    });

    return () => {
      if (sub) sub.unsubscribe();
    };
  }, [gameId, onRoundStart, onPlayerBuzzed, onFirstAnswerCorrect, onStealOpen, onRoundEnd, onMatchFinished]);

  const isInGame = gameId !== null && phase !== 'LOBBY';

  return (
    <div className="min-h-screen bg-dark-950 text-slate-100 flex flex-col">
      <Navbar />

      <main className="flex-1 flex flex-col justify-center">
        {isInGame ? (
          <VersusArenaScreen />
        ) : currentView === 'PROFILE' ? (
          <ProfileScreen onBack={() => setCurrentView('LOBBY')} />
        ) : (
          <LobbyScreen />
        )}
      </main>

      {/* Footer minimaliste */}
      <footer className="border-t border-dark-900 py-4 text-center text-xs text-slate-400">
        BeatRival &bull; Blind Test Compétitif ELO &bull; Propulsé par Deezer API &amp; Spring Boot 3
      </footer>
    </div>
  );
};

export default App;

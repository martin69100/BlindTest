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
  const [isAuthenticating, setIsAuthenticating] = useState(() => {
    return window.location.pathname.includes('/auth/callback') || window.location.pathname.includes('/auth/classback');
  });
  const [currentView, setCurrentView] = useState<'LOBBY' | 'PROFILE'>('LOBBY');

  // Gestion du retour OAuth2 Google (/auth/callback)
  useEffect(() => {
    const pathname = window.location.pathname;
    if (pathname.includes('/auth/callback') || pathname.includes('/auth/classback')) {
      // 1. Extraction du token depuis les query params ou le hash URL
      const urlParams = new URLSearchParams(window.location.search);
      const hashParams = new URLSearchParams(
        window.location.hash.startsWith('#') ? window.location.hash.substring(1) : window.location.hash
      );

      const token =
        urlParams.get('token') ||
        urlParams.get('accessToken') ||
        urlParams.get('access_token') ||
        urlParams.get('jwt') ||
        hashParams.get('token') ||
        hashParams.get('access_token');

      // 2. Sauvegarde du token dans le stockage local avant la redirection
      if (token) {
        localStorage.setItem('blindtest_token', token);
      }

      // 3. Interception et récupération du profil avant de rediriger vers le lobby
      const fetchProfile = async () => {
        try {
          const userData = await authService.getMe(token || undefined);
          setUser(userData);
        } catch (firstErr) {
          console.warn('Premier essai /auth/me échoué, nouvelle tentative...', firstErr);
          // Petite temporisation (600ms) pour absorber un cold start
          await new Promise((r) => setTimeout(r, 600));
          try {
            const retryData = await authService.getMe(token || undefined);
            setUser(retryData);
          } catch (err) {
            console.error('Erreur finale de récupération du profil Google :', err);
          }
        } finally {
          window.history.replaceState({}, document.title, '/');
          setIsAuthenticating(false);
        }
      };

      fetchProfile();
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

  if (isAuthenticating) {
    return (
      <div className="min-h-screen bg-dark-950 text-slate-100 flex flex-col items-center justify-center space-y-4">
        <div className="w-12 h-12 border-4 border-brand-500 border-t-transparent rounded-full animate-spin" />
        <div className="text-lg font-bold text-white">Connexion avec Google en cours...</div>
        <p className="text-xs text-slate-400">Interception des accès et initialisation du profil...</p>
      </div>
    );
  }

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

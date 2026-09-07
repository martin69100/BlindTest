import React, { useEffect, useState } from 'react';
import {
  Swords,
  Dumbbell,
  Sparkles,
  Disc,
  Flame,
  Music,
  Radio,
  Mic,
  ChevronRight,
  Users,
  KeyRound,
  Plus,
  Shield,
  X,
  AlertCircle,
  Loader2,
} from 'lucide-react';
import type { Theme, MatchmakingStats } from '../types';
import { themeService, matchmakingService, soloService, customLobbyService, GOOGLE_AUTH_URL } from '../services/api';
import { useAuthStore } from '../store/useAuthStore';
import { useGameStore } from '../store/useGameStore';
import { wsService } from '../services/websocket';
import { MatchmakingRadarScreen } from './MatchmakingRadarScreen';

export const LobbyScreen: React.FC = () => {
  const { user } = useAuthStore();
  const { initGame, setActiveLobby } = useGameStore();

  const DEFAULT_THEMES: Theme[] = [
    { id: '1', code: 'ANNEES_80', name: 'Années 80', description: 'Synthpop & disco', trackCount: 40 },
    { id: '2', code: 'ANNEES_60', name: 'Années 60', description: 'Sixties & soul', trackCount: 40 },
    { id: '3', code: 'ROCK', name: 'Rock & Métal', description: 'Riffs légendaires', trackCount: 40 },
    { id: '4', code: 'POP', name: 'Pop Hits', description: 'Tubes mondiaux', trackCount: 40 },
    { id: '5', code: 'RAP_FR', name: 'Rap Français', description: 'Classiques & bangers', trackCount: 40 },
    { id: '6', code: 'ANNEES_2000', name: 'Années 2000', description: 'Hits 2000s', trackCount: 40 },
  ];

  const [themes, setThemes] = useState<Theme[]>(DEFAULT_THEMES);
  const [selectedThemeId, setSelectedThemeId] = useState<string | null>(null);
  const [isSearchingMatch, setIsSearchingMatch] = useState(false);
  const [matchStats, setMatchStats] = useState<MatchmakingStats>({
    inQueue: 0,
    inGame: 0,
    activeMatches: 0,
  });

  // États pour la création et jonction de salon personnalisé
  const [isCreatingLobby, setIsCreatingLobby] = useState(false);
  const [showJoinModal, setShowJoinModal] = useState(false);
  const [joinCodeInput, setJoinCodeInput] = useState('');
  const [isJoiningLobby, setIsJoiningLobby] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  useEffect(() => {
    themeService.getThemes()
      .then((data) => {
        if (data && data.length > 0) setThemes(data);
      })
      .catch((err) => {
        console.warn("Backend non encore connecté, utilisation des thèmes locaux :", err.message);
      });
  }, []);

  // Détection du paramètre d'invitation ?lobby=CODE dans l'URL
  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const lobbyParam = urlParams.get('lobby');
    if (lobbyParam) {
      const code = lobbyParam.trim().toUpperCase();
      if (user) {
        setIsJoiningLobby(true);
        customLobbyService.joinLobby(code, user)
          .then((lobby) => {
            setActiveLobby(lobby);
            window.history.replaceState({}, document.title, window.location.pathname);
          })
          .catch((err) => {
            setJoinError(err.response?.data?.message || err.message || "Impossible de rejoindre le salon invité.");
            setJoinCodeInput(code);
            setShowJoinModal(true);
          })
          .finally(() => setIsJoiningLobby(false));
      } else {
        // Mémoriser le code dans la session pour rejoindre dès la connexion OAuth
        sessionStorage.setItem('pending_lobby_code', code);
      }
    }
  }, [user, setActiveLobby]);

  // Récupération initiale et écoute en temps réel des joueurs en recherche et en jeu
  useEffect(() => {
    matchmakingService.getStats()
      .then((data) => {
        if (data) setMatchStats(data);
      })
      .catch((err) => {
        console.warn("Impossible de charger les stats matchmaking :", err.message);
      });

    const sub = wsService.subscribeToMatchmakingStats((stats: MatchmakingStats) => {
      setMatchStats(stats);
    });

    return () => {
      if (sub && typeof sub.unsubscribe === 'function') {
        sub.unsubscribe();
      }
    };
  }, []);

  // Souscription STOMP au canal de matchmaking si connecté
  useEffect(() => {
    if (!user) return;

    const sub = wsService.subscribeToMatchmaking(user.id, (payload) => {
      if (payload.event === 'MATCH_FOUND') {
        setIsSearchingMatch(false);
        initGame(
          payload.gameId,
          false,
          payload.player1Id,
          payload.player2Id,
          payload.player1Name,
          payload.player2Name
        );
      }
    });

    return () => {
      if (sub) sub.unsubscribe();
    };
  }, [user, initGame]);

  const handleStartVersus = async () => {
    if (!user) return;
    try {
      await matchmakingService.joinQueue(user.id, selectedThemeId || undefined);
      setIsSearchingMatch(true);
    } catch (e) {
      console.error("Erreur lors de l'entrée en file :", e);
    }
  };

  const handleStartSolo = async () => {
    if (!user) return;
    try {
      const res = await soloService.startSession(user.id, selectedThemeId || undefined);
      initGame(res.gameId, true, user.id, undefined, user.displayName);
    } catch (e) {
      console.error("Erreur lors du démarrage solo :", e);
    }
  };

  // Créer un salon personnalisé
  const handleCreateLobby = async () => {
    if (!user || isCreatingLobby) return;
    setIsCreatingLobby(true);
    try {
      const activeTheme = themes.find((t) => t.id === selectedThemeId);
      const lobby = await customLobbyService.createLobby(
        user,
        selectedThemeId || undefined,
        activeTheme ? activeTheme.name : undefined,
        10
      );
      setActiveLobby(lobby);
    } catch (e: any) {
      console.error("Erreur création salon personnalisé :", e);
      alert(e.response?.data?.message || "Impossible de créer le salon pour le moment.");
    } finally {
      setIsCreatingLobby(false);
    }
  };

  // Rejoindre un salon personnalisé par code
  const handleJoinLobby = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const code = joinCodeInput.trim().toUpperCase();
    if (!user || !code || isJoiningLobby) return;

    setIsJoiningLobby(true);
    setJoinError(null);
    try {
      const lobby = await customLobbyService.joinLobby(code, user);
      setActiveLobby(lobby);
      setShowJoinModal(false);
      setJoinCodeInput('');
      window.history.replaceState({}, document.title, window.location.pathname);
    } catch (err: any) {
      setJoinError(err.response?.data?.message || err.message || "Code introuvable ou salon inaccessible.");
    } finally {
      setIsJoiningLobby(false);
    }
  };

  const getThemeIcon = (code: string) => {
    switch (code) {
      case 'ANNEES_80': return <Radio className="w-5 h-5 text-fuchsia-400" />;
      case 'ANNEES_60': return <Disc className="w-5 h-5 text-amber-400" />;
      case 'ROCK': return <Flame className="w-5 h-5 text-rose-400" />;
      case 'POP': return <Sparkles className="w-5 h-5 text-cyan-400" />;
      case 'RAP_FR': return <Mic className="w-5 h-5 text-emerald-400" />;
      default: return <Music className="w-5 h-5 text-brand-400" />;
    }
  };

  // Si on est en recherche de match, afficher le radar
  if (isSearchingMatch) {
    const activeTheme = themes.find((t) => t.id === selectedThemeId);
    return (
      <MatchmakingRadarScreen
        themeName={activeTheme ? activeTheme.name : 'Tous thèmes confondus'}
        stats={matchStats}
        onCancel={() => setIsSearchingMatch(false)}
      />
    );
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Connexion avec Google si non connecté */}
      {!user && (
        <div className="bg-dark-900/90 border border-brand-500/40 rounded-3xl p-8 mb-10 shadow-2xl max-w-md mx-auto backdrop-blur-sm text-center">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center mx-auto mb-4 shadow-xl shadow-brand-500/20">
            <Music className="w-8 h-8 text-white" />
          </div>

          <h2 className="text-xl font-black text-white mb-1.5">Rejoindre la compétition</h2>
          <p className="text-xs text-slate-400 mb-6 max-w-xs mx-auto">
            Connectez-vous avec votre compte Google pour enregistrer votre ELO, vos victoires et vos statistiques.
          </p>

          {/* Bouton Google OAuth2 */}
          <a
            href={GOOGLE_AUTH_URL}
            className="w-full bg-white hover:bg-slate-100 text-slate-900 font-bold py-3.5 px-6 rounded-2xl flex items-center justify-center space-x-3 transition-all shadow-xl hover:shadow-white/10 active:scale-98 cursor-pointer group"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
            </svg>
            <span className="text-sm font-bold text-slate-900 group-hover:text-black">Continuer avec Google</span>
          </a>

          <p className="text-[11px] text-slate-500 mt-4">
            Connexion instantanée et sécurisée via Google
          </p>
        </div>
      )}

      {/* Titre & Sous-titre */}
      <div className="text-center mb-10">
        <h2 className="text-3xl sm:text-4xl font-black text-white tracking-tight mb-3">
          Prêt à tester votre oreille musicale ?
        </h2>
        <p className="text-slate-400 text-sm max-w-xl mx-auto">
          Défiez un joueur à votre niveau ELO, lancez un salon privé avec vos amis ou entraînez-vous en solo.
        </p>
      </div>

      {/* Sélection des Thèmes */}
      <div className="mb-10">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-slate-300 uppercase tracking-wider">Sélectionnez un Thème</h3>
          {selectedThemeId && (
            <button
              onClick={() => setSelectedThemeId(null)}
              className="text-xs text-brand-400 hover:underline font-semibold cursor-pointer"
            >
              Réinitialiser (Tous thèmes)
            </button>
          )}
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {/* Option Tous Thèmes */}
          <div
            onClick={() => setSelectedThemeId(null)}
            className={`p-4 rounded-2xl border cursor-pointer transition-all flex flex-col items-center text-center ${
              selectedThemeId === null
                ? 'bg-brand-600/20 border-brand-500 shadow-lg shadow-brand-500/20 scale-102'
                : 'bg-dark-900/60 border-slate-800 hover:border-slate-700'
            }`}
          >
            <div className="w-10 h-10 rounded-xl bg-brand-500/20 flex items-center justify-center mb-2">
              <Sparkles className="w-5 h-5 text-brand-400" />
            </div>
            <h4 className="text-xs font-bold text-white mb-1">Général</h4>
            <span className="text-[10px] text-slate-400 font-medium">Tous thèmes</span>
          </div>

          {/* Liste des thèmes */}
          {themes.map((theme) => {
            const isSelected = selectedThemeId === theme.id;
            return (
              <div
                key={theme.id}
                onClick={() => setSelectedThemeId(theme.id)}
                className={`p-4 rounded-2xl border cursor-pointer transition-all flex flex-col items-center text-center ${
                  isSelected
                    ? 'bg-brand-600/20 border-brand-500 shadow-lg shadow-brand-500/20 scale-102'
                    : 'bg-dark-900/60 border-slate-800 hover:border-slate-700'
                }`}
              >
                <div className="w-10 h-10 rounded-xl bg-dark-800 flex items-center justify-center mb-2">
                  {getThemeIcon(theme.code)}
                </div>
                <h4 className="text-xs font-bold text-white mb-1 truncate max-w-full">{theme.name}</h4>
                <span className="text-[10px] text-slate-400 font-medium">{theme.trackCount || 30}+ titres</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Sélection du Mode de Jeu (3 Grandes Cartes) */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Mode Versus Classé */}
        <div className="relative group bg-gradient-to-br from-dark-900 to-dark-950 border border-brand-500/40 hover:border-brand-500 rounded-3xl p-6 sm:p-7 shadow-xl transition-all hover:shadow-2xl hover:shadow-brand-500/10 flex flex-col justify-between">
          <div>
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-brand-600 to-rose-600 flex items-center justify-center mb-5 shadow-lg shadow-brand-500/30">
              <Swords className="w-6 h-6 text-white" />
            </div>
            <div className="flex flex-wrap items-center gap-1.5 mb-3">
              <span className="text-[10px] font-extrabold uppercase tracking-wider text-rose-400 bg-rose-500/10 px-2.5 py-0.5 rounded-full border border-rose-500/20">
                Compétitif &bull; ELO
              </span>
              <div className="flex items-center space-x-1 bg-emerald-500/10 border border-emerald-500/25 px-2 py-0.5 rounded-full text-[10px] font-bold text-emerald-400">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                </span>
                <span>{matchStats.inQueue} en file</span>
              </div>
            </div>
            <h3 className="text-xl font-black text-white mb-2">Versus Classé (1v1)</h3>
            <p className="text-xs text-slate-400 mb-6 leading-relaxed">
              Match 1v1 en temps réel. 10 manches de 20s avec vol de main. Le résultat impacte directement votre rang ELO officiel.
            </p>
          </div>

          <button
            onClick={handleStartVersus}
            disabled={!user}
            className="w-full bg-gradient-to-r from-brand-600 to-rose-600 hover:from-brand-500 hover:to-rose-500 disabled:opacity-50 text-white font-bold py-3.5 px-5 rounded-2xl flex items-center justify-center space-x-2 shadow-lg shadow-brand-600/30 transition-transform active:scale-98 cursor-pointer"
          >
            <span>Trouver un match ELO</span>
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>

        {/* NOUVEAU : Salon Personnalisé (Multijoueur Amical) */}
        <div className="relative group bg-gradient-to-br from-dark-900 to-dark-950 border border-indigo-500/40 hover:border-indigo-500 rounded-3xl p-6 sm:p-7 shadow-xl transition-all hover:shadow-2xl hover:shadow-indigo-500/10 flex flex-col justify-between">
          <div>
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-indigo-600 to-brand-600 flex items-center justify-center mb-5 shadow-lg shadow-indigo-500/30">
              <Users className="w-6 h-6 text-white" />
            </div>
            <div className="flex flex-wrap items-center gap-1.5 mb-3">
              <span className="text-[10px] font-extrabold uppercase tracking-wider text-indigo-400 bg-indigo-500/10 px-2.5 py-0.5 rounded-full border border-indigo-500/20">
                Salon Privé &bull; 2+ Joueurs
              </span>
              <span className="text-[10px] font-extrabold uppercase tracking-wider text-emerald-400 bg-emerald-500/10 px-2.5 py-0.5 rounded-full border border-emerald-500/20 flex items-center space-x-1">
                <Shield className="w-3 h-3 text-emerald-400" />
                <span>Sans impact ELO</span>
              </span>
            </div>
            <h3 className="text-xl font-black text-white mb-2">Partie Personnalisée</h3>
            <p className="text-xs text-slate-400 mb-6 leading-relaxed">
              Créez un salon privé entre amis avec lien d'invitation. Choisissez votre thème, le nombre de manches, et rejouez à volonté avec podium final !
            </p>
          </div>

          <div className="space-y-2.5">
            <button
              onClick={handleCreateLobby}
              disabled={!user || isCreatingLobby}
              className="w-full bg-gradient-to-r from-indigo-600 to-brand-600 hover:from-indigo-500 hover:to-brand-500 disabled:opacity-50 text-white font-bold py-3 px-5 rounded-2xl flex items-center justify-center space-x-2 shadow-lg shadow-indigo-500/25 transition-transform active:scale-98 cursor-pointer text-xs sm:text-sm"
            >
              {isCreatingLobby ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Création du salon...</span>
                </>
              ) : (
                <>
                  <Plus className="w-4 h-4" />
                  <span>Créer un salon</span>
                </>
              )}
            </button>

            <button
              onClick={() => setShowJoinModal(true)}
              disabled={!user}
              className="w-full bg-dark-800 hover:bg-dark-700 disabled:opacity-50 text-slate-300 hover:text-white border border-slate-700 font-bold py-2.5 px-5 rounded-2xl flex items-center justify-center space-x-2 transition-all active:scale-98 cursor-pointer text-xs"
            >
              <KeyRound className="w-3.5 h-3.5 text-indigo-400" />
              <span>Rejoindre avec un code</span>
            </button>
          </div>
        </div>

        {/* Mode Solo Entraînement */}
        <div className="relative group bg-gradient-to-br from-dark-900 to-dark-950 border border-slate-800 hover:border-slate-700 rounded-3xl p-6 sm:p-7 shadow-xl transition-all hover:shadow-2xl flex flex-col justify-between">
          <div>
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-cyan-600 to-blue-600 flex items-center justify-center mb-5 shadow-lg shadow-cyan-500/30">
              <Dumbbell className="w-6 h-6 text-white" />
            </div>
            <div className="flex flex-wrap items-center gap-1.5 mb-3">
              <span className="text-[10px] font-extrabold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-2.5 py-0.5 rounded-full border border-cyan-500/20">
                Solo &bull; Sans Pression
              </span>
            </div>
            <h3 className="text-xl font-black text-white mb-2">Entraînement Solo</h3>
            <p className="text-xs text-slate-400 mb-6 leading-relaxed">
              Perfectionnez vos connaissances à votre propre rythme. Idéal pour découvrir les extraits musicaux et s'entraîner aux réflexes du buzzer.
            </p>
          </div>

          <button
            onClick={handleStartSolo}
            disabled={!user}
            className="w-full bg-dark-800 hover:bg-dark-700 disabled:opacity-50 text-slate-200 border border-slate-700 font-bold py-3.5 px-5 rounded-2xl flex items-center justify-center space-x-2 transition-transform active:scale-98 cursor-pointer"
          >
            <span>Démarrer l’entraînement</span>
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Modale Rejoindre un Salon avec un Code */}
      {showJoinModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
          <div className="bg-dark-900 border border-indigo-500/40 rounded-3xl p-6 sm:p-8 max-w-md w-full shadow-2xl relative">
            <button
              onClick={() => {
                setShowJoinModal(false);
                setJoinError(null);
              }}
              className="absolute top-5 right-5 text-slate-400 hover:text-white p-1 rounded-lg hover:bg-dark-800 transition-colors cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="w-14 h-14 rounded-2xl bg-indigo-500/20 text-indigo-400 flex items-center justify-center mx-auto mb-4 border border-indigo-500/30">
              <KeyRound className="w-7 h-7" />
            </div>

            <h3 className="text-xl font-black text-white text-center mb-1">
              Rejoindre un salon privé
            </h3>
            <p className="text-xs text-slate-400 text-center mb-6">
              Entrez le code à 6 lettres/chiffres communiqué par votre ami ou collez l'URL d'invitation.
            </p>

            <form onSubmit={handleJoinLobby} className="space-y-4">
              <div>
                <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-2">
                  Code du salon (6 caractères)
                </label>
                <input
                  type="text"
                  maxLength={10}
                  value={joinCodeInput}
                  onChange={(e) => {
                    setJoinCodeInput(e.target.value.toUpperCase());
                    setJoinError(null);
                  }}
                  placeholder="EX: A7K9X2"
                  autoFocus
                  className="w-full bg-dark-950 border border-slate-700 focus:border-indigo-500 rounded-2xl py-3.5 px-4 text-center text-xl font-mono font-black text-white tracking-widest placeholder-slate-600 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition-all uppercase"
                />
              </div>

              {joinError && (
                <div className="flex items-center space-x-2 text-xs text-rose-400 bg-rose-500/10 border border-rose-500/30 rounded-xl p-3">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{joinError}</span>
                </div>
              )}

              <div className="flex space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    setShowJoinModal(false);
                    setJoinError(null);
                  }}
                  className="flex-1 bg-dark-800 hover:bg-dark-700 text-slate-300 font-bold py-3 rounded-xl border border-slate-700 text-xs transition-colors cursor-pointer"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={joinCodeInput.trim().length < 4 || isJoiningLobby}
                  className="flex-1 bg-gradient-to-r from-indigo-600 to-brand-600 hover:from-indigo-500 hover:to-brand-500 disabled:opacity-50 text-white font-bold py-3 rounded-xl text-xs transition-transform active:scale-98 shadow-lg shadow-indigo-600/30 flex items-center justify-center space-x-2 cursor-pointer"
                >
                  {isJoiningLobby ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Connexion...</span>
                    </>
                  ) : (
                    <span>Rejoindre le salon</span>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

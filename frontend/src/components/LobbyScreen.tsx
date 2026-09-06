import React, { useEffect, useState } from 'react';
import { Swords, Dumbbell, Sparkles, Disc, Flame, Music, Radio, Mic, ChevronRight } from 'lucide-react';
import type { Theme } from '../types';
import { themeService, matchmakingService, soloService, GOOGLE_AUTH_URL } from '../services/api';
import { useAuthStore } from '../store/useAuthStore';
import { useGameStore } from '../store/useGameStore';
import { wsService } from '../services/websocket';
import { MatchmakingRadarScreen } from './MatchmakingRadarScreen';

export const LobbyScreen: React.FC = () => {
  const { user } = useAuthStore();
  const { initGame } = useGameStore();

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

  useEffect(() => {
    themeService.getThemes()
      .then((data) => {
        if (data && data.length > 0) setThemes(data);
      })
      .catch((err) => {
        console.warn("Backend non encore connecté, utilisation des thèmes locaux :", err.message);
      });
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
          Choisissez un thème ou jouez en mode général, défiez un joueur à votre niveau ELO ou entraînez-vous en solo.
        </p>
      </div>

      {/* Sélection des Thèmes */}
      <div className="mb-10">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-slate-300 uppercase tracking-wider">Sélectionnez un Thème</h3>
          {selectedThemeId && (
            <button
              onClick={() => setSelectedThemeId(null)}
              className="text-xs text-brand-400 hover:underline font-semibold"
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

      {/* Sélection du Mode de Jeu (Grandes Cartes d'Action) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Mode Versus Classé */}
        <div className="relative group bg-gradient-to-br from-dark-900 to-dark-950 border border-brand-500/40 hover:border-brand-500 rounded-3xl p-6 sm:p-8 shadow-xl transition-all hover:shadow-2xl hover:shadow-brand-500/10 flex flex-col justify-between">
          <div>
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-brand-600 to-rose-600 flex items-center justify-center mb-6 shadow-lg shadow-brand-500/30">
              <Swords className="w-7 h-7 text-white" />
            </div>
            <span className="text-[11px] font-extrabold uppercase tracking-wider text-rose-400 bg-rose-500/10 px-3 py-1 rounded-full border border-rose-500/20">
              Compétitif &bull; ELO
            </span>
            <h3 className="text-2xl font-black text-white mt-3 mb-2">Mode Versus Classé</h3>
            <p className="text-xs sm:text-sm text-slate-400 mb-6">
              Affrontez un adversaire en temps réel. 10 manches de 20s. Buzzer ultra-réactif, vol de main et calcul d'ELO à l'issue de la partie.
            </p>
          </div>

          <button
            onClick={handleStartVersus}
            disabled={!user}
            className="w-full bg-gradient-to-r from-brand-600 to-rose-600 hover:from-brand-500 hover:to-rose-500 disabled:opacity-50 text-white font-bold py-4 px-6 rounded-2xl flex items-center justify-center space-x-2 shadow-lg shadow-brand-600/30 transition-transform active:scale-98 cursor-pointer"
          >
            <span>Trouver un match ELO</span>
            <ChevronRight className="w-5 h-5" />
          </button>
        </div>

        {/* Mode Solo Entraînement */}
        <div className="relative group bg-gradient-to-br from-dark-900 to-dark-950 border border-slate-800 hover:border-slate-700 rounded-3xl p-6 sm:p-8 shadow-xl transition-all hover:shadow-2xl flex flex-col justify-between">
          <div>
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-cyan-600 to-blue-600 flex items-center justify-center mb-6 shadow-lg shadow-cyan-500/30">
              <Dumbbell className="w-7 h-7 text-white" />
            </div>
            <span className="text-[11px] font-extrabold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-3 py-1 rounded-full border border-cyan-500/20">
              Solo &bull; Sans Pression
            </span>
            <h3 className="text-2xl font-black text-white mt-3 mb-2">Mode Entraînement</h3>
            <p className="text-xs sm:text-sm text-slate-400 mb-6">
              Perfectionnez vos connaissances sur un thème ciblé. Les morceaux s'enchaînent pour enrichir votre niveau de maîtrise.
            </p>
          </div>

          <button
            onClick={handleStartSolo}
            disabled={!user}
            className="w-full bg-dark-800 hover:bg-dark-700 disabled:opacity-50 text-slate-200 border border-slate-700 font-bold py-4 px-6 rounded-2xl flex items-center justify-center space-x-2 transition-transform active:scale-98 cursor-pointer"
          >
            <span>Démarrer l’entraînement</span>
            <ChevronRight className="w-5 h-5" />
          </button>
        </div>
      </div>
    </div>
  );
};

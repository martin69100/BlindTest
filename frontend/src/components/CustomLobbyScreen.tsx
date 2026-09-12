import React, { useEffect, useState } from 'react';
import {
  Crown,
  Copy,
  Check,
  Users,
  Play,
  RotateCcw,
  ArrowLeft,
  Music,
  Disc,
  Flame,
  Sparkles,
  Radio,
  Mic,
  Trophy,
  Shield,
  Clock,
  Zap,
} from 'lucide-react';
import type { LobbyData, Theme, LobbyParticipant } from '../types';
import { customLobbyService, themeService } from '../services/api';
import { useAuthStore } from '../store/useAuthStore';
import { useGameStore } from '../store/useGameStore';
import { wsService } from '../services/websocket';

interface CustomLobbyScreenProps {
  initialLobby: LobbyData;
  onLeave: () => void;
}

export const CustomLobbyScreen: React.FC<CustomLobbyScreenProps> = ({ initialLobby, onLeave }) => {
  const { user } = useAuthStore();
  const { initCustomGame, setActiveLobby } = useGameStore();

  const [lobby, setLobby] = useState<LobbyData>(initialLobby);
  const [themes, setThemes] = useState<Theme[]>([]);
  const [copied, setCopied] = useState(false);
  const [isStarting, setIsStarting] = useState(false);

  const isHost = Boolean(user && lobby.hostId === user.id);
  const participantsList: LobbyParticipant[] = Array.isArray(lobby.participants)
    ? lobby.participants
    : Object.values(lobby.participants || {});

  // Chargement des thèmes
  useEffect(() => {
    themeService.getThemes().then((data) => {
      if (data && data.length > 0) setThemes(data);
    }).catch(err => console.warn('Impossible de charger les thèmes :', err));
  }, []);

  // Synchronisation WebSocket sur /topic/lobby/{code}
  useEffect(() => {
    if (!lobby.code) return;

    const sub = wsService.subscribeToLobby(lobby.code, (payload) => {
      if (payload.event === 'LOBBY_UPDATED' && payload.lobby) {
        setLobby(payload.lobby);
        setActiveLobby(payload.lobby);
      } else if (payload.event === 'LOBBY_GAME_START') {
        setIsStarting(false);
        initCustomGame(
          payload.gameId,
          payload.lobbyCode,
          payload.roundsCount || 10,
          payload.gameMode || 'BUZZER',
          payload.teamMode || 'INDIVIDUAL'
        );
      } else if (payload.event === 'LOBBY_RETURN') {
        // Retour dans le salon
        customLobbyService.getLobby(lobby.code).then((fresh) => {
          if (fresh) {
            setLobby(fresh);
            setActiveLobby(fresh);
          }
        });
      }
    });

    return () => {
      if (sub && typeof sub.unsubscribe === 'function') {
        sub.unsubscribe();
      }
    };
  }, [lobby.code, initCustomGame, setActiveLobby]);

  const handleCopyLink = () => {
    const inviteUrl = `${window.location.origin}?lobby=${lobby.code}`;
    navigator.clipboard.writeText(inviteUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  const handleUpdateTheme = async (themeId: string | null, themeName: string) => {
    if (!isHost || !user) return;
    try {
      const updated = await customLobbyService.updateSettings(lobby.code, {
        requestingUserId: user.id,
        themeId: themeId || undefined,
        themeName,
        roundsCount: lobby.roundsCount,
        gameMode: lobby.gameMode || 'BUZZER',
        teamMode: lobby.teamMode || 'INDIVIDUAL',
      });
      setLobby(updated);
    } catch (e) {
      console.error('Erreur mise à jour thème :', e);
    }
  };

  const handleUpdateRounds = async (roundsCount: number) => {
    if (!isHost || !user) return;
    try {
      const updated = await customLobbyService.updateSettings(lobby.code, {
        requestingUserId: user.id,
        themeId: lobby.themeId,
        themeName: lobby.themeName,
        roundsCount,
        gameMode: lobby.gameMode || 'BUZZER',
        teamMode: lobby.teamMode || 'INDIVIDUAL',
      });
      setLobby(updated);
    } catch (e) {
      console.error('Erreur mise à jour manches :', e);
    }
  };

  const handleUpdateGameMode = async (gameMode: 'BUZZER' | 'NO_BUZZER') => {
    if (!isHost || !user) return;
    try {
      const updated = await customLobbyService.updateSettings(lobby.code, {
        requestingUserId: user.id,
        themeId: lobby.themeId,
        themeName: lobby.themeName,
        roundsCount: lobby.roundsCount,
        gameMode,
        teamMode: lobby.teamMode || 'INDIVIDUAL',
      });
      setLobby(updated);
    } catch (e) {
      console.error('Erreur mise à jour mode de jeu :', e);
    }
  };

  const handleUpdateTeamMode = async (teamMode: 'INDIVIDUAL' | 'TEAMS') => {
    if (!isHost || !user) return;
    try {
      const updated = await customLobbyService.updateSettings(lobby.code, {
        requestingUserId: user.id,
        themeId: lobby.themeId,
        themeName: lobby.themeName,
        roundsCount: lobby.roundsCount,
        gameMode: lobby.gameMode || 'BUZZER',
        teamMode,
      });
      setLobby(updated);
    } catch (e) {
      console.error('Erreur mise à jour mode équipe :', e);
    }
  };

  const handleSwitchTeam = async (targetTeam: 'BLUE' | 'RED') => {
    if (!user) return;
    try {
      const updated = await customLobbyService.switchTeam(lobby.code, user.id, targetTeam);
      setLobby(updated);
    } catch (e) {
      console.error('Erreur changement d’équipe :', e);
    }
  };

  const handleStartGame = async () => {
    if (!isHost || !user || isStarting) return;
    setIsStarting(true);
    try {
      await customLobbyService.startGame(lobby.code, user.id);
    } catch (e) {
      console.error('Erreur lancement de partie :', e);
      setIsStarting(false);
    }
  };

  const handleLeave = async () => {
    if (user) {
      try {
        await customLobbyService.leaveLobby(lobby.code, user.id);
      } catch (e) {
        console.warn('Erreur départ salon :', e);
      }
    }
    onLeave();
  };

  const getThemeIcon = (code?: string) => {
    switch (code) {
      case 'ANNEES_80': return <Radio className="w-4 h-4 text-fuchsia-400" />;
      case 'ANNEES_60': return <Disc className="w-4 h-4 text-amber-400" />;
      case 'ROCK': return <Flame className="w-4 h-4 text-rose-400" />;
      case 'POP': return <Sparkles className="w-4 h-4 text-cyan-400" />;
      case 'RAP_FR': return <Mic className="w-4 h-4 text-emerald-400" />;
      default: return <Music className="w-4 h-4 text-brand-400" />;
    }
  };

  const hasPreviousGame = lobby.status === 'FINISHED' || (lobby.lastGameLeaderboard && lobby.lastGameLeaderboard.length > 0);

  return (
    <div className="max-w-4xl mx-auto px-4 py-6 flex flex-col justify-between min-h-[85vh] animate-in fade-in duration-300">
      {/* Barre Supérieure du Salon */}
      <div className="bg-dark-900/90 border border-slate-800 rounded-3xl p-5 mb-6 shadow-2xl backdrop-blur-md">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <button
              onClick={handleLeave}
              className="p-2.5 rounded-xl bg-dark-800 hover:bg-dark-700 text-slate-400 hover:text-white border border-slate-700/80 transition-all cursor-pointer"
              title="Quitter le salon"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">Salon Privé</span>
                <span className="bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-[10px] font-extrabold px-2 py-0.5 rounded-full">
                  Partie Amicale
                </span>
              </div>
              <h2 className="text-2xl font-black text-white tracking-wide flex items-center space-x-2">
                <span>Code :</span>
                <span className="font-mono text-brand-400 tracking-wider bg-brand-500/10 border border-brand-500/30 px-3 py-0.5 rounded-xl">
                  {lobby.code}
                </span>
              </h2>
            </div>
          </div>

          {/* Bouton de Partage / Copie du lien */}
          <div className="flex items-center space-x-2">
            <button
              onClick={handleCopyLink}
              className="flex items-center space-x-2 bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 text-white font-bold px-4 py-2.5 rounded-xl shadow-lg shadow-brand-500/20 active:scale-95 transition-all text-xs cursor-pointer"
            >
              {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
              <span>{copied ? 'Lien copié dans le presse-papier !' : "Copier le lien d'invitation"}</span>
            </button>
          </div>
        </div>

        {/* Message d'info ELO */}
        <div className="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">
          <div className="flex items-center space-x-1.5">
            <Shield className="w-4 h-4 text-brand-400 shrink-0" />
            <span>
              Les rangs <strong>ELO</strong> sont affichés pour le prestige, mais les victoires/défaites n'ont <strong>aucun impact</strong> sur votre score ELO officiel.
            </span>
          </div>
        </div>
      </div>

      {/* Paramètres de la partie (Hôte vs Joueur) */}
      <div className="bg-dark-900/60 border border-slate-800/80 rounded-2xl p-5 mb-6">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider flex items-center space-x-2">
            <span>Configuration de la partie</span>
            {!isHost && <span className="text-[10px] text-slate-500 font-normal">(Gérée par l'hôte)</span>}
          </h3>
          <div className="text-xs text-slate-400 font-mono">
            Thème actuel : <span className="font-bold text-white">{lobby.themeName || 'Tous thèmes'}</span> &bull; {lobby.roundsCount} manches
          </div>
        </div>

        {/* Sélection Thèmes pour l'hôte */}
        {isHost ? (
          <div className="space-y-3">
            <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2">
              <button
                type="button"
                onClick={() => handleUpdateTheme(null, 'Tous thèmes')}
                className={`p-2.5 rounded-xl border text-left transition-all flex items-center space-x-2 cursor-pointer ${
                  lobby.themeId === null || !lobby.themeId
                    ? 'bg-brand-600/20 border-brand-500 text-white shadow-md shadow-brand-500/10'
                    : 'bg-dark-800/60 border-slate-700/60 text-slate-400 hover:text-white hover:border-slate-600'
                }`}
              >
                <Sparkles className="w-4 h-4 text-brand-400 shrink-0" />
                <span className="text-xs font-bold truncate">Général</span>
              </button>

              {themes.map((t) => {
                const isSelected = lobby.themeId === t.id;
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => handleUpdateTheme(t.id, t.name)}
                    className={`p-2.5 rounded-xl border text-left transition-all flex items-center space-x-2 cursor-pointer ${
                      isSelected
                        ? 'bg-brand-600/20 border-brand-500 text-white shadow-md shadow-brand-500/10'
                        : 'bg-dark-800/60 border-slate-700/60 text-slate-400 hover:text-white hover:border-slate-600'
                    }`}
                  >
                    {getThemeIcon(t.code)}
                    <span className="text-xs font-bold truncate">{t.name}</span>
                  </button>
                );
              })}
            </div>

            {/* Nombre de manches */}
            <div className="flex items-center space-x-3 pt-2">
              <span className="text-xs font-semibold text-slate-400 flex items-center space-x-1 min-w-[140px]">
                <Clock className="w-3.5 h-3.5 text-slate-400" />
                <span>Nombre de manches :</span>
              </span>
              <div className="flex items-center space-x-1.5">
                {[5, 10, 15, 20].map((count) => (
                  <button
                    key={count}
                    type="button"
                    onClick={() => handleUpdateRounds(count)}
                    className={`px-3 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                      lobby.roundsCount === count
                        ? 'bg-brand-500 text-white shadow-sm'
                        : 'bg-dark-800 text-slate-400 hover:text-white hover:bg-dark-700'
                    }`}
                  >
                    {count}
                  </button>
                ))}
              </div>
            </div>

            {/* Mode Buzzer vs Sans Buzzer */}
            <div className="flex flex-col sm:flex-row sm:items-center gap-2 pt-2 border-t border-slate-800/60">
              <span className="text-xs font-semibold text-slate-400 flex items-center space-x-1 min-w-[140px]">
                <Zap className="w-3.5 h-3.5 text-amber-400" />
                <span>Système de réponse :</span>
              </span>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => handleUpdateGameMode('BUZZER')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all flex items-center space-x-1.5 cursor-pointer ${
                    (lobby.gameMode || 'BUZZER') === 'BUZZER'
                      ? 'bg-amber-500 text-dark-950 shadow-md shadow-amber-500/20 font-extrabold'
                      : 'bg-dark-800 text-slate-400 hover:text-white hover:bg-dark-700 border border-slate-700/60'
                  }`}
                >
                  <span>⚡ Avec Buzzer (Classique)</span>
                </button>
                <button
                  type="button"
                  onClick={() => handleUpdateGameMode('NO_BUZZER')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all flex items-center space-x-1.5 cursor-pointer ${
                    lobby.gameMode === 'NO_BUZZER'
                      ? 'bg-emerald-500 text-dark-950 shadow-md shadow-emerald-500/20 font-extrabold'
                      : 'bg-dark-800 text-slate-400 hover:text-white hover:bg-dark-700 border border-slate-700/60'
                  }`}
                >
                  <span>🎵 Sans Buzzer (Saisie libre pendant le son)</span>
                </button>
              </div>
            </div>

            {/* Mode Individuel vs Par Équipe */}
            <div className="flex flex-col sm:flex-row sm:items-center gap-2 pt-2 border-t border-slate-800/60">
              <span className="text-xs font-semibold text-slate-400 flex items-center space-x-1 min-w-[140px]">
                <Users className="w-3.5 h-3.5 text-brand-400" />
                <span>Format de partie :</span>
              </span>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => handleUpdateTeamMode('INDIVIDUAL')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all flex items-center space-x-1.5 cursor-pointer ${
                    (lobby.teamMode || 'INDIVIDUAL') === 'INDIVIDUAL'
                      ? 'bg-brand-500 text-white shadow-md shadow-brand-500/20 font-extrabold'
                      : 'bg-dark-800 text-slate-400 hover:text-white hover:bg-dark-700 border border-slate-700/60'
                  }`}
                >
                  <span>👤 Individuel (Chacun pour soi)</span>
                </button>
                <button
                  type="button"
                  onClick={() => handleUpdateTeamMode('TEAMS')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all flex items-center space-x-1.5 cursor-pointer ${
                    lobby.teamMode === 'TEAMS'
                      ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/20 font-extrabold'
                      : 'bg-dark-800 text-slate-400 hover:text-white hover:bg-dark-700 border border-slate-700/60'
                  }`}
                >
                  <span>👥 Par Équipes (Bleu vs Rouge)</span>
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-3 text-xs text-slate-400 bg-dark-950/40 p-3 rounded-xl border border-slate-800/50">
            <div className="flex items-center space-x-2">
              <span className="text-slate-500">Thème :</span>
              <span className="font-bold text-white bg-dark-800 px-2.5 py-1 rounded-lg border border-slate-700">
                {lobby.themeName || 'Tous thèmes'}
              </span>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-slate-500">Manches :</span>
              <span className="font-bold text-white bg-dark-800 px-2.5 py-1 rounded-lg border border-slate-700">
                {lobby.roundsCount}
              </span>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-slate-500">Réponse :</span>
              <span className="font-bold text-white bg-dark-800 px-2.5 py-1 rounded-lg border border-slate-700">
                {lobby.gameMode === 'NO_BUZZER' ? '🎵 Saisie libre' : '⚡ Avec Buzzer'}
              </span>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-slate-500">Format :</span>
              <span className="font-bold text-white bg-dark-800 px-2.5 py-1 rounded-lg border border-slate-700">
                {lobby.teamMode === 'TEAMS' ? '👥 Par Équipes (Bleu vs Rouge)' : '👤 Individuel'}
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Podium ou Récap de la partie précédente si retour au lobby */}
      {hasPreviousGame && (
        <div className="bg-gradient-to-r from-amber-500/10 via-brand-500/10 to-indigo-500/10 border border-amber-500/30 rounded-2xl p-4 mb-6 shadow-xl">
          <div className="flex items-center space-x-2 mb-3">
            <Trophy className="w-4 h-4 text-amber-400" />
            <h4 className="text-xs font-black uppercase tracking-wider text-amber-300">
              Classement de la dernière partie
            </h4>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {(lobby.lastGameLeaderboard || []).slice(0, 3).map((player, idx) => (
              <div
                key={player.playerId}
                className="bg-dark-900/80 border border-slate-800 rounded-xl p-3 flex items-center justify-between"
              >
                <div className="flex items-center space-x-2.5">
                  <div className="w-7 h-7 rounded-full bg-dark-800 border border-slate-700 flex items-center justify-center font-black text-xs text-amber-400">
                    {idx === 0 ? '🥇' : idx === 1 ? '🥈' : '🥉'}
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs font-black text-white truncate max-w-[120px]">{player.playerName}</p>
                    <p className="text-[10px] text-slate-400">{player.elo || 1000} ELO</p>
                  </div>
                </div>
                <div className="text-base font-black text-amber-400 bg-amber-500/10 px-2.5 py-0.5 rounded-lg border border-amber-500/20">
                  {player.score} pts
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Liste des Participants */}
      <div className="mb-6 flex-1">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-slate-300 uppercase tracking-wider flex items-center space-x-2">
            <Users className="w-4 h-4 text-brand-400" />
            <span>Joueurs dans le salon ({participantsList.length})</span>
          </h3>
          <span className="text-xs text-slate-500">
            {participantsList.length > 1 ? 'Prêts à en découdre' : 'En attente d’autres joueurs...'}
          </span>
        </div>

        {lobby.teamMode === 'TEAMS' ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Colonne Équipe Bleue */}
            <div className="bg-blue-950/20 border border-blue-500/30 rounded-3xl p-4 shadow-xl flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-3 pb-2 border-b border-blue-500/20">
                  <div className="flex items-center space-x-2">
                    <span className="text-lg">🔵</span>
                    <h4 className="text-sm font-black text-blue-400 uppercase tracking-wider">
                      Équipe Bleue ({participantsList.filter((p) => (p.team || 'BLUE') === 'BLUE').length})
                    </h4>
                  </div>
                  {user && (participantsList.find((p) => p.userId === user.id)?.team || 'BLUE') !== 'BLUE' && (
                    <button
                      type="button"
                      onClick={() => handleSwitchTeam('BLUE')}
                      className="text-xs font-bold bg-blue-600 hover:bg-blue-500 text-white px-3 py-1.5 rounded-xl shadow transition-all cursor-pointer"
                    >
                      Rejoindre les Bleus
                    </button>
                  )}
                </div>
                <div className="space-y-2.5">
                  {participantsList.filter((p) => (p.team || 'BLUE') === 'BLUE').map((p) => {
                    const isMe = user && p.userId === user.id;
                    return (
                      <div
                        key={p.userId}
                        className={`bg-dark-900 border rounded-2xl p-4 flex items-center justify-between shadow-lg transition-all ${
                          isMe ? 'border-blue-500 bg-blue-500/10' : 'border-blue-500/30 bg-blue-950/20'
                        }`}
                      >
                        <div className="flex items-center space-x-3 min-w-0">
                          <div className="relative shrink-0">
                            {p.avatarUrl ? (
                              <img
                                src={p.avatarUrl}
                                alt={p.displayName}
                                className="w-11 h-11 rounded-xl object-cover border border-slate-700"
                              />
                            ) : (
                              <div className="w-11 h-11 rounded-xl bg-gradient-to-tr from-blue-600 to-cyan-600 flex items-center justify-center font-black text-white text-base">
                                {p.displayName.charAt(0).toUpperCase()}
                              </div>
                            )}
                            {p.isHost && (
                              <div
                                className="absolute -top-1.5 -right-1.5 bg-amber-500 text-dark-950 p-0.5 rounded-full shadow-md"
                                title="Hôte de la partie"
                              >
                                <Crown className="w-3.5 h-3.5 fill-current" />
                              </div>
                            )}
                          </div>

                          <div className="min-w-0">
                            <div className="flex items-center space-x-1.5">
                              <h4 className="text-sm font-black text-white truncate max-w-[120px]">
                                {p.displayName}
                              </h4>
                              {isMe && (
                                <span className="text-[10px] font-extrabold uppercase bg-blue-500/30 text-blue-200 px-1.5 py-0.2 rounded">
                                  Moi
                                </span>
                              )}
                            </div>

                            <div className="flex items-center space-x-2 mt-0.5">
                              <span className="text-xs font-mono font-bold text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.2 rounded-md">
                                {p.elo || 1000} ELO
                              </span>
                              {p.lastGameScore !== undefined && p.lastGameScore > 0 && (
                                <span className="text-[10px] text-slate-400 font-medium">
                                  Score : {p.lastGameScore}
                                </span>
                              )}
                            </div>
                          </div>
                        </div>

                        <div className="shrink-0 flex items-center space-x-1 text-blue-400 bg-blue-500/10 border border-blue-500/25 px-2.5 py-1 rounded-full text-[11px] font-bold">
                          <span className="w-2 h-2 rounded-full bg-blue-400 animate-pulse"></span>
                          <span>Prêt</span>
                        </div>
                      </div>
                    );
                  })}
                  {participantsList.filter((p) => (p.team || 'BLUE') === 'BLUE').length === 0 && (
                    <p className="text-xs text-slate-500 text-center py-4 italic">Aucun joueur dans l'équipe bleue</p>
                  )}
                </div>
              </div>
            </div>

            {/* Colonne Équipe Rouge */}
            <div className="bg-rose-950/20 border border-rose-500/30 rounded-3xl p-4 shadow-xl flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-3 pb-2 border-b border-rose-500/20">
                  <div className="flex items-center space-x-2">
                    <span className="text-lg">🔴</span>
                    <h4 className="text-sm font-black text-rose-400 uppercase tracking-wider">
                      Équipe Rouge ({participantsList.filter((p) => p.team === 'RED').length})
                    </h4>
                  </div>
                  {user && (participantsList.find((p) => p.userId === user.id)?.team || 'BLUE') !== 'RED' && (
                    <button
                      type="button"
                      onClick={() => handleSwitchTeam('RED')}
                      className="text-xs font-bold bg-rose-600 hover:bg-rose-500 text-white px-3 py-1.5 rounded-xl shadow transition-all cursor-pointer"
                    >
                      Rejoindre les Rouges
                    </button>
                  )}
                </div>
                <div className="space-y-2.5">
                  {participantsList.filter((p) => p.team === 'RED').map((p) => {
                    const isMe = user && p.userId === user.id;
                    return (
                      <div
                        key={p.userId}
                        className={`bg-dark-900 border rounded-2xl p-4 flex items-center justify-between shadow-lg transition-all ${
                          isMe ? 'border-rose-500 bg-rose-500/10' : 'border-rose-500/30 bg-rose-950/20'
                        }`}
                      >
                        <div className="flex items-center space-x-3 min-w-0">
                          <div className="relative shrink-0">
                            {p.avatarUrl ? (
                              <img
                                src={p.avatarUrl}
                                alt={p.displayName}
                                className="w-11 h-11 rounded-xl object-cover border border-slate-700"
                              />
                            ) : (
                              <div className="w-11 h-11 rounded-xl bg-gradient-to-tr from-rose-600 to-amber-600 flex items-center justify-center font-black text-white text-base">
                                {p.displayName.charAt(0).toUpperCase()}
                              </div>
                            )}
                            {p.isHost && (
                              <div
                                className="absolute -top-1.5 -right-1.5 bg-amber-500 text-dark-950 p-0.5 rounded-full shadow-md"
                                title="Hôte de la partie"
                              >
                                <Crown className="w-3.5 h-3.5 fill-current" />
                              </div>
                            )}
                          </div>

                          <div className="min-w-0">
                            <div className="flex items-center space-x-1.5">
                              <h4 className="text-sm font-black text-white truncate max-w-[120px]">
                                {p.displayName}
                              </h4>
                              {isMe && (
                                <span className="text-[10px] font-extrabold uppercase bg-rose-500/30 text-rose-200 px-1.5 py-0.2 rounded">
                                  Moi
                                </span>
                              )}
                            </div>

                            <div className="flex items-center space-x-2 mt-0.5">
                              <span className="text-xs font-mono font-bold text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.2 rounded-md">
                                {p.elo || 1000} ELO
                              </span>
                              {p.lastGameScore !== undefined && p.lastGameScore > 0 && (
                                <span className="text-[10px] text-slate-400 font-medium">
                                  Score : {p.lastGameScore}
                                </span>
                              )}
                            </div>
                          </div>
                        </div>

                        <div className="shrink-0 flex items-center space-x-1 text-rose-400 bg-rose-500/10 border border-rose-500/25 px-2.5 py-1 rounded-full text-[11px] font-bold">
                          <span className="w-2 h-2 rounded-full bg-rose-400 animate-pulse"></span>
                          <span>Prêt</span>
                        </div>
                      </div>
                    );
                  })}
                  {participantsList.filter((p) => p.team === 'RED').length === 0 && (
                    <p className="text-xs text-slate-500 text-center py-4 italic">Aucun joueur dans l'équipe rouge</p>
                  )}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {participantsList.map((p) => {
              const isMe = user && p.userId === user.id;
              return (
                <div
                  key={p.userId}
                  className={`bg-dark-900 border rounded-2xl p-4 flex items-center justify-between shadow-lg transition-all ${
                    isMe ? 'border-brand-500/50 bg-brand-500/5' : 'border-slate-800'
                  }`}
                >
                  <div className="flex items-center space-x-3 min-w-0">
                    {/* Avatar */}
                    <div className="relative shrink-0">
                      {p.avatarUrl ? (
                        <img
                          src={p.avatarUrl}
                          alt={p.displayName}
                          className="w-11 h-11 rounded-xl object-cover border border-slate-700"
                        />
                      ) : (
                        <div className="w-11 h-11 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-600 flex items-center justify-center font-black text-white text-base">
                          {p.displayName.charAt(0).toUpperCase()}
                        </div>
                      )}
                      {p.isHost && (
                        <div
                          className="absolute -top-1.5 -right-1.5 bg-amber-500 text-dark-950 p-0.5 rounded-full shadow-md"
                          title="Hôte de la partie"
                        >
                          <Crown className="w-3.5 h-3.5 fill-current" />
                        </div>
                      )}
                    </div>

                    {/* Pseudo et badges */}
                    <div className="min-w-0">
                      <div className="flex items-center space-x-1.5">
                        <h4 className="text-sm font-black text-white truncate max-w-[120px]">
                          {p.displayName}
                        </h4>
                        {isMe && (
                          <span className="text-[10px] font-extrabold uppercase bg-brand-500/20 text-brand-300 px-1.5 py-0.2 rounded">
                            Moi
                          </span>
                        )}
                      </div>

                      {/* Affichage explicite de l'ELO dans le lobby */}
                      <div className="flex items-center space-x-2 mt-0.5">
                        <span className="text-xs font-mono font-bold text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.2 rounded-md">
                          {p.elo || 1000} ELO
                        </span>
                        {p.lastGameScore !== undefined && p.lastGameScore > 0 && (
                          <span className="text-[10px] text-slate-400 font-medium">
                            Score : {p.lastGameScore}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Statut Prêt */}
                  <div className="shrink-0 flex items-center space-x-1 text-emerald-400 bg-emerald-500/10 border border-emerald-500/25 px-2.5 py-1 rounded-full text-[11px] font-bold">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                    <span>Prêt</span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Barre d'action inférieure */}
      <div className="bg-dark-900 border border-slate-800 rounded-3xl p-4 shadow-2xl flex flex-wrap items-center justify-between gap-4">
        <div className="text-xs text-slate-400">
          {participantsList.length < 2 ? (
            <span>Envoyez le lien à vos amis pour qu'ils vous rejoignent. Vous pouvez également tester seul.</span>
          ) : (
            <span>{participantsList.length} joueurs connectés &bull; Partie synchronisée en temps réel</span>
          )}
        </div>

        {isHost ? (
          <button
            onClick={handleStartGame}
            disabled={isStarting}
            className="bg-gradient-to-r from-brand-600 via-rose-600 to-indigo-600 hover:from-brand-500 hover:via-rose-500 hover:to-indigo-500 disabled:opacity-50 text-white font-extrabold px-8 py-3.5 rounded-2xl flex items-center space-x-2 shadow-xl shadow-brand-500/25 transition-transform active:scale-95 cursor-pointer text-sm"
          >
            {hasPreviousGame ? (
              <>
                <RotateCcw className="w-5 h-5" />
                <span>{isStarting ? 'Lancement en cours...' : 'Relancer une nouvelle partie'}</span>
              </>
            ) : (
              <>
                <Play className="w-5 h-5 fill-current" />
                <span>{isStarting ? 'Lancement en cours...' : 'Lancer la partie'}</span>
              </>
            )}
          </button>
        ) : (
          <div className="flex items-center space-x-2 text-xs font-bold text-amber-400 bg-amber-500/10 border border-amber-500/30 px-4 py-2.5 rounded-xl">
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span>
            <span>En attente du lancement par l'hôte ({lobby.hostName})...</span>
          </div>
        )}
      </div>
    </div>
  );
};

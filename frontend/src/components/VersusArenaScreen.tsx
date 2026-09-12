import React, { useEffect, useState } from 'react';
import {
  Swords,
  LogOut,
  Flag,
  AlertTriangle,
  SkipForward,
  Dumbbell,
  Timer,
  X,
  Users,
  Zap,
} from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';
import { wsService } from '../services/websocket';
import { SecureAudioPlayer } from './SecureAudioPlayer';
import { BuzzerButton } from './BuzzerButton';
import { AnswerInput } from './AnswerInput';
import { RoundRevealCard } from './RoundRevealCard';
import { MatchVictoryModal } from './MatchVictoryModal';
import { FreeAnswerInput } from './FreeAnswerInput';

export const VersusArenaScreen: React.FC = () => {
  const {
    gameId,
    phase,
    roundNumber,
    totalRounds,
    player2Id,
    player1Name,
    player2Name,
    player1Score,
    player2Score,
    isSolo,
    isCustom,
    gameMode,
    teamMode,
    teamScores,
    playerTeams,
    leaderboard,
    playerScores,
    activeLobby,
    failedPlayerIds,
    buzzerPlayerId,
    remainingAudioMs,
    lastWrongGuess,
    resetGame,
    returnToCustomLobby,
  } = useGameStore();
  const { user } = useAuthStore();

  const isPlayer2 = !isSolo && !isCustom && Boolean(user && player2Id && user.id === player2Id);
  const myScore = isCustom && user && playerScores[user.id] !== undefined
    ? playerScores[user.id]
    : (isPlayer2 ? player2Score : player1Score);
  const opponentScore = isPlayer2 ? player1Score : player2Score;
  const opponentDisplayName = isSolo
    ? 'Score Max'
    : (isPlayer2 ? player1Name : player2Name) || 'Adversaire';

  // Alerte réponse fausse d'un adversaire
  const opponentWrongGuess =
    !isSolo && lastWrongGuess && user && lastWrongGuess.playerId !== user.id
      ? lastWrongGuess
      : null;

  const [readySent, setReadySent] = useState(false);
  const [audioProgress, setAudioProgress] = useState(100);
  const [showForfeitModal, setShowForfeitModal] = useState(false);

  // Secondes restantes estimées pour l'écoute
  const secondsRemaining = Math.max(0, Math.ceil((audioProgress / 100) * 20));

  // Signaler au serveur qu'on est prêt dès l'arrivée dans l'arène
  useEffect(() => {
    if (gameId && user && !readySent) {
      wsService.sendReady(gameId, user.id);
      setReadySent(true);
    }
  }, [gameId, user, readySent]);

  // Barre de progression des 20 secondes max d'écoute
  useEffect(() => {
    if (phase === 'PLAYING') {
      const totalMs = 20000;
      const startTime = Date.now();
      const initialRemaining = remainingAudioMs || totalMs;

      const interval = setInterval(() => {
        const elapsed = Date.now() - startTime;
        const current = Math.max(0, initialRemaining - elapsed);
        setAudioProgress((current / totalMs) * 100);

        if (current <= 0) {
          clearInterval(interval);
        }
      }, 100);

      return () => clearInterval(interval);
    }
  }, [phase, remainingAudioMs]);

  const handleConfirmForfeit = () => {
    setShowForfeitModal(false);
    if (!gameId || !user) return;

    if (isSolo) {
      wsService.sendForfeit(gameId, user.id);
      resetGame();
    } else if (isCustom) {
      wsService.sendForfeit(gameId, user.id);
      returnToCustomLobby();
    } else {
      wsService.sendForfeit(gameId, user.id);
    }
  };

  // Liste des participants ordonnée pour le tableau multijoueur
  const multiplayerParticipants = React.useMemo(() => {
    if (!isCustom) return [];

    // Priorité au classement en direct du serveur
    if (leaderboard && leaderboard.length > 0) {
      return leaderboard;
    }

    // Sinon extraire depuis activeLobby
    if (activeLobby?.participants) {
      const list = Array.isArray(activeLobby.participants)
        ? activeLobby.participants
        : Object.values(activeLobby.participants);

      return list
        .map((p) => ({
          playerId: p.userId,
          playerName: p.displayName,
          score: playerScores[p.userId] ?? p.lastGameScore ?? 0,
          elo: p.elo ?? 1000,
        }))
        .sort((a, b) => b.score - a.score);
    }

    return [];
  }, [isCustom, leaderboard, activeLobby, playerScores]);

  return (
    <div className="max-w-4xl mx-auto px-4 py-6 flex flex-col items-center justify-between min-h-[85vh]">
      {/* Barre Supérieure Ergonomique : Mode, Progression de Manche, Timer & Audio intégré */}
      <div className="w-full bg-slate-900/70 border border-slate-800/80 rounded-2xl p-3.5 mb-6 shadow-xl backdrop-blur-md">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-2.5">
          {/* Gauche : Mode de jeu épuré & Progression des manches */}
          <div className="flex items-center space-x-2 sm:space-x-3">
            {/* Badge de Mode Élégant */}
            <div
              className={`flex items-center space-x-1.5 px-3 py-1 rounded-full text-[11px] font-black tracking-wider uppercase border shadow-sm ${
                isSolo
                  ? 'bg-cyan-500/10 text-cyan-400 border-cyan-500/30'
                  : isCustom
                  ? 'bg-indigo-500/10 text-indigo-300 border-indigo-500/30'
                  : 'bg-rose-500/10 text-rose-400 border-rose-500/30'
              }`}
            >
              {isSolo ? (
                <Dumbbell className="w-3.5 h-3.5" />
              ) : isCustom ? (
                <Users className="w-3.5 h-3.5 text-indigo-400" />
              ) : (
                <Swords className="w-3.5 h-3.5" />
              )}
              <span>{isSolo ? 'Entraînement' : isCustom ? 'Salon Privé (Sans ELO)' : 'Versus Classé'}</span>
            </div>

            {/* Badge Manche Actuelle */}
            <div className="flex items-center space-x-1.5 px-3 py-1 bg-slate-950/60 border border-slate-800 rounded-full text-[11px] font-mono font-bold text-slate-300 shadow-inner">
              <span className="text-slate-400">Manche</span>
              <span className="text-white font-extrabold">{roundNumber || 1}</span>
              <span className="text-slate-600">/</span>
              <span className="text-slate-400">{totalRounds}</span>
            </div>

            {/* Traqueur de progression sous forme de petits indicateurs ronds */}
            <div className="hidden lg:flex items-center space-x-1 pl-1">
              {Array.from({ length: totalRounds }).map((_, idx) => (
                <div
                  key={idx}
                  className={`w-1.5 h-1.5 rounded-full transition-all duration-300 ${
                    idx + 1 < (roundNumber || 1)
                      ? 'bg-brand-500'
                      : idx + 1 === (roundNumber || 1)
                      ? 'bg-amber-400 ring-2 ring-amber-400/30 scale-125'
                      : 'bg-slate-800'
                  }`}
                  title={`Manche ${idx + 1}`}
                />
              ))}
            </div>
          </div>

          {/* Centre : Chronomètre d'écoute (uniquement pendant l'écoute) */}
          {phase === 'PLAYING' && (
            <div
              className={`flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-mono font-extrabold border transition-all ${
                secondsRemaining > 5
                  ? 'bg-brand-500/10 text-brand-400 border-brand-500/30'
                  : 'bg-rose-500/15 text-rose-400 border-rose-500/40 animate-pulse'
              }`}
            >
              <Timer className="w-3.5 h-3.5" />
              <span>{secondsRemaining}s</span>
            </div>
          )}

          {/* Droite : Contrôle Son persistant & Bouton Quitter discret */}
          <div className="flex items-center space-x-2">
            {/* Lecteur audio toujours monté pour préserver le volume et l'élément HTML5 */}
            <SecureAudioPlayer />

            {/* Bouton Quitter / Abandonner ergonomique et discret */}
            <button
              onClick={() => setShowForfeitModal(true)}
              className="flex items-center space-x-1.5 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 p-2 sm:px-3 sm:py-1.5 rounded-xl transition-all border border-transparent hover:border-rose-500/20 cursor-pointer text-xs font-semibold"
              title={isSolo ? "Quitter l'entraînement" : isCustom ? "Quitter le salon privé" : "Abandonner la partie classée"}
            >
              {isSolo ? <LogOut className="w-3.5 h-3.5" /> : <Flag className="w-3.5 h-3.5" />}
              <span className="hidden sm:inline">{isSolo ? 'Quitter' : isCustom ? 'Quitter' : 'Abandonner'}</span>
            </button>
          </div>
        </div>

        {/* Barre de chrono 20 secondes intégrée et fluide */}
        <div className="w-full h-1.5 bg-dark-950 rounded-full overflow-hidden border border-slate-800/80">
          <div
            className={`h-full transition-all duration-100 ease-linear rounded-full ${
              audioProgress > 30
                ? 'bg-gradient-to-r from-cyan-500 via-brand-500 to-indigo-500 shadow-sm shadow-brand-500/40'
                : 'bg-gradient-to-r from-amber-500 to-rose-500 animate-pulse'
            }`}
            style={{ width: `${audioProgress}%` }}
          />
        </div>
      </div>

      {/* Bannière Scores Équipe */}
      {isCustom && teamMode === 'TEAMS' && (
        <div className="w-full bg-dark-900/90 border border-slate-800 rounded-2xl p-4 mb-5 shadow-xl flex items-center justify-around">
          {/* Équipe Bleue */}
          <div
            className={`flex items-center space-x-3 px-4 py-2.5 rounded-xl border transition-all ${
              user && (playerTeams[user.id] || 'BLUE') === 'BLUE'
                ? 'bg-blue-600/20 border-blue-500/60 shadow-md shadow-blue-500/10'
                : 'bg-dark-950/60 border-slate-800/80'
            }`}
          >
            <span className="text-2xl">🔵</span>
            <div>
              <div className="flex items-center space-x-1.5">
                <span className="text-xs font-black text-blue-400 uppercase tracking-wider">Équipe Bleue</span>
                {user && (playerTeams[user.id] || 'BLUE') === 'BLUE' && (
                  <span className="text-[9px] bg-blue-500/30 text-blue-200 px-1.5 py-0.2 rounded font-extrabold">
                    Mon équipe
                  </span>
                )}
              </div>
              <p className="text-2xl font-mono font-black text-white">
                {teamScores['BLUE'] ?? 0} <span className="text-xs text-blue-400 font-normal">pts</span>
              </p>
            </div>
          </div>

          <span className="text-sm font-black text-slate-500 uppercase tracking-widest px-2">VS</span>

          {/* Équipe Rouge */}
          <div
            className={`flex items-center space-x-3 px-4 py-2.5 rounded-xl border transition-all ${
              user && playerTeams[user.id] === 'RED'
                ? 'bg-rose-600/20 border-rose-500/60 shadow-md shadow-rose-500/10'
                : 'bg-dark-950/60 border-slate-800/80'
            }`}
          >
            <span className="text-2xl">🔴</span>
            <div>
              <div className="flex items-center space-x-1.5">
                <span className="text-xs font-black text-rose-400 uppercase tracking-wider">Équipe Rouge</span>
                {user && playerTeams[user.id] === 'RED' && (
                  <span className="text-[9px] bg-rose-500/30 text-rose-200 px-1.5 py-0.2 rounded font-extrabold">
                    Mon équipe
                  </span>
                )}
              </div>
              <p className="text-2xl font-mono font-black text-white">
                {teamScores['RED'] ?? 0} <span className="text-xs text-rose-400 font-normal">pts</span>
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Tableau des Scores : Multijoueur Personnalisé VS 1v1 Classé */}
      {isCustom && multiplayerParticipants.length > 0 ? (
        <div className="w-full bg-dark-900/80 border border-slate-800/80 rounded-2xl p-3.5 mb-6 shadow-xl">
          <div className="flex items-center justify-between mb-2.5 px-1">
            <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-400 flex items-center space-x-1.5">
              <Users className="w-3.5 h-3.5" />
              <span>Classement en direct ({multiplayerParticipants.length} joueurs)</span>
            </span>
            <span className="text-[10px] text-slate-500 font-mono">
              Score &bull; Rang ELO affiché
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2.5">
            {multiplayerParticipants.map((p, idx) => {
              const isMe = user && p.playerId === user.id;
              const hasFailed = failedPlayerIds?.includes(p.playerId);
              const isCurrentBuzzer = buzzerPlayerId === p.playerId;
              const pTeam = playerTeams[p.playerId] || (activeLobby?.participants ? (Array.isArray(activeLobby.participants) ? activeLobby.participants.find((x: any) => x.userId === p.playerId)?.team : (activeLobby.participants as any)[p.playerId]?.team) : undefined);

              return (
                <div
                  key={p.playerId}
                  className={`relative p-2.5 rounded-xl border transition-all flex items-center justify-between ${
                    isMe
                      ? 'bg-brand-600/15 border-brand-500/60 shadow-md shadow-brand-500/10'
                      : 'bg-dark-950/60 border-slate-800/80'
                  }`}
                >
                  <div className="flex items-center space-x-2 min-w-0 flex-1 pr-1.5">
                    {/* Badge de rang */}
                    <div className="w-5 h-5 rounded-md bg-dark-800 border border-slate-700/80 flex items-center justify-center font-black text-[10px] text-slate-300 shrink-0">
                      {idx === 0 ? '🥇' : idx === 1 ? '🥈' : idx === 2 ? '🥉' : idx + 1}
                    </div>

                    <div className="min-w-0 flex-1">
                      <div className="flex items-center space-x-1">
                        {teamMode === 'TEAMS' && (
                          <span className="text-[10px] shrink-0">{pTeam === 'RED' ? '🔴' : '🔵'}</span>
                        )}
                        <span className={`text-xs font-black truncate ${isMe ? 'text-white' : 'text-slate-300'}`}>
                          {p.playerName}
                        </span>
                        {isMe && (
                          <span className="text-[9px] font-extrabold bg-brand-500/30 text-brand-300 px-1 rounded shrink-0">
                            Moi
                          </span>
                        )}
                      </div>

                      {/* Statuts temps réel (Buzzer ou Erreur sur cette manche) */}
                      <div className="flex items-center space-x-1 mt-0.5">
                        {isCurrentBuzzer ? (
                          <span className="text-[9px] font-bold text-rose-400 bg-rose-500/20 px-1.5 py-0.2 rounded animate-pulse flex items-center space-x-0.5">
                            <Zap className="w-2.5 h-2.5" />
                            <span>Buzzer</span>
                          </span>
                        ) : hasFailed ? (
                          <span className="text-[9px] font-semibold text-rose-500/80 bg-rose-500/10 px-1 rounded line-through">
                            Bloqué
                          </span>
                        ) : (
                          <span className="text-[9px] text-amber-400/80 font-mono">
                            {p.elo || 1000} ELO
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Score */}
                  <div className="text-base font-black font-mono text-brand-400 bg-dark-800/90 px-2.5 py-0.5 rounded-lg border border-slate-700/60 shrink-0">
                    {p.score ?? 0}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      ) : (
        /* Tableau des Scores Face à Face (1v1 ou Solo) */
        <div className="w-full grid grid-cols-2 gap-4 mb-6">
          {/* Joueur 1 (Moi) */}
          <div className="bg-dark-900 border border-brand-500/30 rounded-2xl p-4 flex items-center justify-between shadow-lg">
            <div>
              <p className="text-xs font-bold text-slate-400 uppercase tracking-wider">Vous</p>
              <h4 className="text-sm sm:text-base font-black text-white truncate max-w-[120px]">
                {user?.displayName || 'Joueur'}
              </h4>
            </div>
            <div className="text-2xl sm:text-3xl font-black text-brand-400 bg-brand-500/10 px-4 py-1.5 rounded-xl border border-brand-500/30">
              {myScore}
            </div>
          </div>

          {/* Joueur 2 (Adversaire ou Cible Solo) */}
          <div className="bg-dark-900 border border-slate-800 rounded-2xl p-4 flex items-center justify-between shadow-lg">
            <div>
              <p className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                {isSolo ? 'Objectif' : 'Adversaire'}
              </p>
              <h4 className="text-sm sm:text-base font-black text-slate-300 truncate max-w-[120px]">
                {opponentDisplayName}
              </h4>
            </div>
            <div className="text-2xl sm:text-3xl font-black text-slate-400 bg-dark-800 px-4 py-1.5 rounded-xl border border-slate-700">
              {isSolo ? totalRounds * 2 : opponentScore}
            </div>
          </div>
        </div>
      )}

      {/* Alerte en temps réel : Réponse fausse d'un adversaire */}
      {opponentWrongGuess && phase !== 'REVEAL' && phase !== 'FINISHED' && (
        <div className="w-full max-w-md mb-4 bg-rose-500/10 border border-rose-500/40 rounded-2xl px-4 py-3 flex items-center justify-between shadow-lg shadow-rose-500/10 animate-in fade-in slide-in-from-top-2 duration-300">
          <div className="flex items-center space-x-3 min-w-0">
            <div className="w-8 h-8 rounded-xl bg-rose-500/20 text-rose-400 flex items-center justify-center shrink-0 border border-rose-500/30">
              <X className="w-4 h-4 stroke-[3]" />
            </div>
            <div className="min-w-0">
              <p className="text-[11px] font-extrabold text-rose-400 uppercase tracking-wider">
                {opponentWrongGuess.playerName || 'Un adversaire'} s'est trompé
              </p>
              <p className="text-sm font-black text-white truncate">
                « {opponentWrongGuess.guess} »
              </p>
            </div>
          </div>
          <span className="text-[10px] font-black uppercase bg-rose-500/20 text-rose-300 border border-rose-500/30 px-2.5 py-1 rounded-full shrink-0 ml-2">
            Faux
          </span>
        </div>
      )}

      {/* Scène Centrale Dynamique */}
      <div className="w-full flex-1 flex flex-col items-center justify-center my-4">
        {phase === 'REVEAL' ? (
          <RoundRevealCard />
        ) : phase === 'BUZZED' || phase === 'BONUS' ? (
          <AnswerInput />
        ) : gameMode === 'NO_BUZZER' ? (
          <FreeAnswerInput />
        ) : (
          <div className="flex flex-col items-center justify-center space-y-4">
            <BuzzerButton />
            {isSolo && (
              <button
                onClick={() => {
                  if (gameId && user) {
                    wsService.sendSkipRound(gameId, user.id);
                  }
                }}
                className="flex items-center space-x-2 text-xs font-bold text-amber-400 hover:text-amber-300 bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 px-5 py-2.5 rounded-full transition-all shadow-md active:scale-95 cursor-pointer mt-2"
                title="Passer ce morceau (0 point) et afficher immédiatement la solution"
              >
                <SkipForward className="w-3.5 h-3.5" />
                <span>Passer ce morceau (afficher la réponse)</span>
              </button>
            )}
          </div>
        )}
      </div>

      {/* Règle & Info Vol de Main */}
      <div className="text-center text-[11px] text-slate-500 font-medium space-y-1">
        <p>
          {gameMode === 'NO_BUZZER'
            ? 'Règle : Saisie libre pendant les 20s • +1 pt Titre • +1 pt Artiste • Tout le monde joue en continu !'
            : isCustom
            ? 'Règle : 1er buzz valide Titre ou Artiste (+1 pt) • Bonus 10s pour la 2ème info (+1 pt) • Vol de main ouvert • ELO protégé'
            : 'Règle : 1er buzz valide Titre ou Artiste (+1 pt) • Bonus 10s pour la 2ème info (+1 pt) • Vol de main actif'}
        </p>
        {isCustom && teamMode === 'TEAMS' && (
          <p className="text-indigo-400 font-bold">
            👥 Match par équipes : Les points individuels alimentent le score de votre équipe !
          </p>
        )}
      </div>

      {/* Modale de confirmation d'abandon / arrêt de session */}
      {showForfeitModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
          <div className="bg-dark-900 border border-rose-500/50 rounded-3xl p-6 sm:p-8 max-w-sm w-full text-center shadow-2xl">
            <div className="w-14 h-14 rounded-2xl bg-rose-500/20 text-rose-400 flex items-center justify-center mx-auto mb-4 border border-rose-500/30">
              <AlertTriangle className="w-7 h-7" />
            </div>

            <h3 className="text-lg font-black text-white mb-2">
              {isSolo
                ? "Arrêter l'entraînement ?"
                : isCustom
                ? "Quitter le salon privé ?"
                : "Abandonner la partie classée ?"}
            </h3>

            <p className="text-xs text-slate-400 mb-6">
              {isSolo
                ? 'Vous reviendrez au salon principal. Votre score de cette session ne sera pas perdu.'
                : isCustom
                ? 'Vous retournerez au salon privé. Vos amis pourront continuer la partie et votre rang ELO ne sera pas impacté.'
                : 'Attention : abandonner une partie classée sera comptabilisé comme une défaite et réduira votre ELO.'}
            </p>

            <div className="flex space-x-3">
              <button
                onClick={() => setShowForfeitModal(false)}
                className="flex-1 bg-dark-800 hover:bg-dark-700 text-slate-300 font-bold py-2.5 rounded-xl border border-slate-700 text-xs transition-colors cursor-pointer"
              >
                Continuer à jouer
              </button>
              <button
                onClick={handleConfirmForfeit}
                className="flex-1 bg-rose-600 hover:bg-rose-500 text-white font-bold py-2.5 rounded-xl text-xs transition-colors shadow-lg shadow-rose-600/30 cursor-pointer"
              >
                {isSolo ? 'Quitter' : isCustom ? 'Quitter le salon' : 'Confirmer l’abandon'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modale de victoire / fin de partie */}
      <MatchVictoryModal />
    </div>
  );
};

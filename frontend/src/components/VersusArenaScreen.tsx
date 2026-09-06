import React, { useEffect, useState } from 'react';
import { Swords, LogOut, Flag, AlertTriangle, SkipForward, Dumbbell, Timer } from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';
import { wsService } from '../services/websocket';
import { SecureAudioPlayer } from './SecureAudioPlayer';
import { BuzzerButton } from './BuzzerButton';
import { AnswerInput } from './AnswerInput';
import { RoundRevealCard } from './RoundRevealCard';
import { MatchVictoryModal } from './MatchVictoryModal';

export const VersusArenaScreen: React.FC = () => {
  const {
    gameId,
    phase,
    roundNumber,
    totalRounds,
    player1Score,
    player2Score,
    isSolo,
    remainingAudioMs,
    resetGame,
  } = useGameStore();
  const { user } = useAuthStore();

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
    } else {
      wsService.sendForfeit(gameId, user.id);
    }
  };

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
                  : 'bg-rose-500/10 text-rose-400 border-rose-500/30'
              }`}
            >
              {isSolo ? <Dumbbell className="w-3.5 h-3.5" /> : <Swords className="w-3.5 h-3.5" />}
              <span>{isSolo ? 'Entraînement' : 'Versus Classé'}</span>
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
              title={isSolo ? "Quitter l'entraînement" : "Abandonner la partie classée"}
            >
              {isSolo ? <LogOut className="w-3.5 h-3.5" /> : <Flag className="w-3.5 h-3.5" />}
              <span className="hidden sm:inline">{isSolo ? 'Quitter' : 'Abandonner'}</span>
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

      {/* Tableau des Scores Face à Face */}
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
            {player1Score}
          </div>
        </div>

        {/* Joueur 2 (Adversaire ou Cible Solo) */}
        <div className="bg-dark-900 border border-slate-800 rounded-2xl p-4 flex items-center justify-between shadow-lg">
          <div>
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider">
              {isSolo ? 'Objectif' : 'Adversaire'}
            </p>
            <h4 className="text-sm sm:text-base font-black text-slate-300 truncate max-w-[120px]">
              {isSolo ? 'Score Max' : 'Adversaire'}
            </h4>
          </div>
          <div className="text-2xl sm:text-3xl font-black text-slate-400 bg-dark-800 px-4 py-1.5 rounded-xl border border-slate-700">
            {isSolo ? totalRounds * 2 : player2Score}
          </div>
        </div>
      </div>

      {/* Scène Centrale Dynamique */}
      <div className="w-full flex-1 flex flex-col items-center justify-center my-4">
        {phase === 'REVEAL' ? (
          <RoundRevealCard />
        ) : phase === 'BUZZED' || phase === 'BONUS' ? (
          <AnswerInput />
        ) : (
          <div className="flex flex-col items-center justify-center space-y-4">
            <BuzzerButton />
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
          </div>
        )}
      </div>

      {/* Règle Option A / Info Vol de Main */}
      <div className="text-center text-[11px] text-slate-500 font-medium">
        <span>Règle : 1er buzz valide Titre ou Artiste (+1 pt) &bull; Bonus 10s pour la 2ème info (+1 pt) &bull; Vol de main actif</span>
      </div>

      {/* Modale de confirmation d'abandon / arrêt de session */}
      {showForfeitModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
          <div className="bg-dark-900 border border-rose-500/50 rounded-3xl p-6 sm:p-8 max-w-sm w-full text-center shadow-2xl">
            <div className="w-14 h-14 rounded-2xl bg-rose-500/20 text-rose-400 flex items-center justify-center mx-auto mb-4 border border-rose-500/30">
              <AlertTriangle className="w-7 h-7" />
            </div>

            <h3 className="text-lg font-black text-white mb-2">
              {isSolo ? "Arrêter l'entraînement ?" : 'Abandonner la partie classée ?'}
            </h3>

            <p className="text-xs text-slate-400 mb-6">
              {isSolo
                ? 'Vous reviendrez au salon principal. Votre score de cette session ne sera pas perdu.'
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
                {isSolo ? 'Quitter' : 'Confirmer l’abandon'}
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

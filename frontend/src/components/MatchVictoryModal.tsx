import React, { useEffect } from 'react';
import confetti from 'canvas-confetti';
import { Trophy, ArrowLeft, TrendingUp, TrendingDown, Dumbbell, Award } from 'lucide-react';
import { useGameStore } from '../store/useGameStore';
import { useAuthStore } from '../store/useAuthStore';

export const MatchVictoryModal: React.FC = () => {
  const { phase, matchResult, player1Score, player2Score, resetGame, isSolo, totalRounds } = useGameStore();
  const { user, refreshProfile } = useAuthStore();

  useEffect(() => {
    if (phase === 'FINISHED') {
      confetti({
        particleCount: 120,
        spread: 80,
        origin: { y: 0.6 },
      });
      refreshProfile();
    }
  }, [phase, refreshProfile]);

  if (phase !== 'FINISHED') return null;

  const isForfeit = matchResult?.forfeit === true;
  const isWinner = matchResult && (
    (matchResult.winnerId === user?.id) ||
    (user && player1Score > player2Score)
  );
  const isDraw = player1Score === player2Score;

  const eloDelta = matchResult?.player1EloChange || 0;
  const displayScore = player1Score ?? 0;
  const maxScore = totalRounds * 2;

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4">
      <div className="bg-dark-900 border border-brand-500/50 rounded-3xl p-8 max-w-md w-full text-center shadow-2xl animate-in zoom-in-95 duration-300">
        {/* Badge Vainqueur / Entraînement */}
        <div className={`w-20 h-20 rounded-full flex items-center justify-center mx-auto mb-4 shadow-xl ${
          isSolo
            ? 'bg-gradient-to-tr from-cyan-400 to-indigo-500 shadow-cyan-500/30'
            : isWinner
            ? 'bg-gradient-to-tr from-amber-400 to-yellow-500 shadow-amber-500/30'
            : isDraw
            ? 'bg-gradient-to-tr from-slate-400 to-slate-500 shadow-slate-500/30'
            : 'bg-gradient-to-tr from-rose-500 to-amber-600 shadow-rose-500/30'
        }`}>
          {isSolo ? (
            displayScore >= maxScore * 0.7 ? (
              <Trophy className="w-10 h-10 text-dark-950" />
            ) : (
              <Dumbbell className="w-10 h-10 text-dark-950" />
            )
          ) : isWinner ? (
            <Trophy className="w-10 h-10 text-dark-950" />
          ) : (
            <Award className="w-10 h-10 text-dark-950" />
          )}
        </div>

        <h2 className="text-2xl font-black text-white tracking-tight mb-2">
          {isSolo
            ? isForfeit
              ? 'Entraînement interrompu'
              : 'Session d’entraînement terminée !'
            : isDraw
            ? 'Match Nul !'
            : isWinner
            ? 'VICTOIRE !'
            : 'DÉFAITE'}
        </h2>

        {isSolo && !isForfeit && (
          <p className="text-xs text-slate-400 mb-2">
            {displayScore >= maxScore * 0.8
              ? 'Excellente oreille musicale ! Score impressionnant.'
              : displayScore >= maxScore * 0.5
              ? 'Bon entraînement ! Continuez ainsi.'
              : 'Bel effort ! La répétition fait les champions.'}
          </p>
        )}

        {/* Tableau des scores finaux */}
        <div className="flex items-center justify-center space-x-6 my-6 bg-dark-950/70 p-5 rounded-2xl border border-slate-800">
          <div>
            <p className="text-xs text-slate-400 font-semibold mb-1">Votre Score</p>
            <p className="text-3xl font-black text-brand-400">
              {displayScore} <span className="text-lg font-bold text-brand-500/80">pts</span>
            </p>
            {isSolo && (
              <p className="text-xs text-slate-500 font-medium mt-1">sur {maxScore} possibles</p>
            )}
          </div>

          {!isSolo && (
            <>
              <div className="h-10 w-px bg-slate-800" />
              <div>
                <p className="text-xs text-slate-400 font-semibold mb-1">Adversaire</p>
                <p className="text-3xl font-black text-slate-400">
                  {player2Score ?? 0} <span className="text-lg font-bold text-slate-500">pts</span>
                </p>
              </div>
            </>
          )}
        </div>

        {/* Variation d'ELO */}
        {!isSolo && matchResult && (
          <div className="mb-6 p-3 bg-dark-800 rounded-xl border border-slate-700 flex items-center justify-center space-x-2">
            {eloDelta >= 0 ? (
              <TrendingUp className="w-4 h-4 text-emerald-400" />
            ) : (
              <TrendingDown className="w-4 h-4 text-rose-400" />
            )}
            <span className="text-xs font-bold text-slate-300">
              Variation ELO :{' '}
              <span className={eloDelta >= 0 ? 'text-emerald-400' : 'text-rose-400'}>
                {eloDelta >= 0 ? `+${eloDelta}` : eloDelta} pts
              </span>
            </span>
          </div>
        )}

        <button
          onClick={resetGame}
          className="w-full bg-brand-600 hover:bg-brand-500 text-white font-bold py-3.5 px-6 rounded-xl flex items-center justify-center space-x-2 transition-transform active:scale-98 shadow-lg shadow-brand-600/30 cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Retour au salon</span>
        </button>
      </div>
    </div>
  );
};

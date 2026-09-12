import React, { useEffect, useState, useMemo } from 'react';
import {
  Trophy,
  X,
  Search,
  RotateCcw,
  Medal,
  Crown,
  Sparkles,
  Shield,
  Flame,
} from 'lucide-react';
import type { User } from '../types';
import { userService } from '../services/api';

interface LeaderboardModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentUser?: User | null;
}

export const LeaderboardModal: React.FC<LeaderboardModalProps> = ({
  isOpen,
  onClose,
  currentUser,
}) => {
  const [players, setPlayers] = useState<User[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState<string | null>(null);

  const fetchLeaderboard = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await userService.getLeaderboard(50);
      setPlayers(data || []);
    } catch (err: any) {
      console.error('Erreur chargement du classement :', err);
      setError('Impossible de charger le classement pour le moment.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchLeaderboard();
    }
  }, [isOpen]);

  // Fermeture avec la touche Échap
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  // Filtrage par recherche
  const filteredPlayers = useMemo(() => {
    if (!searchQuery.trim()) return players;
    const query = searchQuery.toLowerCase().trim();
    return players.filter((p) =>
      p.displayName?.toLowerCase().includes(query)
    );
  }, [players, searchQuery]);

  // Rang du joueur connecté dans la liste
  const currentUserRank = useMemo(() => {
    if (!currentUser) return null;
    const index = players.findIndex((p) => p.id === currentUser.id);
    return index !== -1 ? index + 1 : null;
  }, [players, currentUser]);

  const getTierInfo = (elo: number) => {
    if (elo >= 1500) {
      return {
        name: 'Grand Maître',
        color: 'text-amber-300 border-amber-500/40 bg-amber-500/10',
        icon: <Crown className="w-3 h-3 text-amber-400" />,
      };
    }
    if (elo >= 1300) {
      return {
        name: 'Diamant',
        color: 'text-cyan-300 border-cyan-500/40 bg-cyan-500/10',
        icon: <Sparkles className="w-3 h-3 text-cyan-400" />,
      };
    }
    if (elo >= 1150) {
      return {
        name: 'Platine',
        color: 'text-emerald-300 border-emerald-500/40 bg-emerald-500/10',
        icon: <Shield className="w-3 h-3 text-emerald-400" />,
      };
    }
    if (elo >= 1000) {
      return {
        name: 'Or',
        color: 'text-violet-300 border-violet-500/40 bg-violet-500/10',
        icon: <Medal className="w-3 h-3 text-violet-400" />,
      };
    }
    return {
      name: 'Challenger',
      color: 'text-slate-400 border-slate-700 bg-slate-800/40',
      icon: <Flame className="w-3 h-3 text-slate-400" />,
    };
  };

  if (!isOpen) return null;

  const top3 = players.slice(0, 3);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 animate-in fade-in duration-200">
      {/* Backdrop sombre */}
      <div
        className="absolute inset-0 bg-dark-950/80 backdrop-blur-md transition-opacity"
        onClick={onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-2xl bg-dark-900 border border-slate-800 rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh] z-10 animate-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="p-5 sm:p-6 border-b border-slate-800 flex items-center justify-between bg-gradient-to-r from-dark-900 via-dark-850 to-dark-900">
          <div className="flex items-center space-x-3.5">
            <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-amber-500 to-yellow-400 flex items-center justify-center shadow-lg shadow-amber-500/20 shrink-0">
              <Trophy className="w-6 h-6 text-dark-950" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="text-lg font-black text-white tracking-wide">
                  Classement Général
                </h3>
                <span className="text-[10px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 border border-amber-500/30">
                  Top 50 ELO
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Les meilleurs compétiteurs de BeatRival
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-2">
            <button
              onClick={fetchLeaderboard}
              disabled={isLoading}
              className="p-2 rounded-xl bg-dark-800 hover:bg-dark-700 text-slate-400 hover:text-white border border-slate-700/80 transition-all cursor-pointer disabled:opacity-50"
              title="Rafraîchir le classement"
            >
              <RotateCcw className={`w-4 h-4 ${isLoading ? 'animate-spin text-brand-400' : ''}`} />
            </button>
            <button
              onClick={onClose}
              className="p-2 rounded-xl bg-dark-800 hover:bg-dark-700 text-slate-400 hover:text-white border border-slate-700/80 transition-all cursor-pointer"
              title="Fermer (Échap)"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Podium Top 3 (si pas de recherche active et au moins 1 joueur) */}
        {!searchQuery && top3.length > 0 && (
          <div className="p-4 sm:px-6 bg-dark-950/40 border-b border-slate-800/80">
            <div className="grid grid-cols-3 gap-2 sm:gap-4 items-end pt-2">
              {/* 2ème place */}
              {top3[1] ? (
                <div className="bg-gradient-to-t from-dark-900 to-slate-800/40 border border-slate-700/70 rounded-2xl p-3 text-center flex flex-col items-center relative shadow-lg order-1">
                  <div className="w-6 h-6 rounded-full bg-slate-700 text-slate-200 border border-slate-500 flex items-center justify-center text-xs font-black -mt-6 mb-1 shadow">
                    🥈
                  </div>
                  <div className="w-10 h-10 rounded-full border-2 border-slate-400 overflow-hidden mb-1.5 bg-dark-800 flex items-center justify-center">
                    {top3[1].avatarUrl ? (
                      <img src={top3[1].avatarUrl} alt={top3[1].displayName} className="w-full h-full object-cover" />
                    ) : (
                      <span className="font-bold text-xs text-white">{top3[1].displayName?.charAt(0)}</span>
                    )}
                  </div>
                  <h4 className="text-xs font-extrabold text-white truncate w-full">
                    {top3[1].displayName}
                  </h4>
                  <span className="text-[11px] font-black text-slate-300 font-mono mt-0.5">
                    {top3[1].elo} <span className="text-[9px] text-slate-400 font-normal">ELO</span>
                  </span>
                </div>
              ) : (
                <div className="order-1" />
              )}

              {/* 1ère place (au centre, surélevée) */}
              {top3[0] && (
                <div className="bg-gradient-to-t from-dark-900 via-amber-950/20 to-amber-500/10 border-2 border-amber-500/60 rounded-2xl p-3.5 text-center flex flex-col items-center relative shadow-xl shadow-amber-500/10 order-2 scale-105 z-10">
                  <div className="w-7 h-7 rounded-full bg-gradient-to-tr from-amber-500 to-yellow-400 text-dark-950 border border-amber-300 flex items-center justify-center text-xs font-black -mt-7 mb-1 shadow-lg shadow-amber-500/30">
                    👑
                  </div>
                  <div className="w-12 h-12 rounded-full border-2 border-amber-400 overflow-hidden mb-1.5 bg-dark-800 flex items-center justify-center shadow-md shadow-amber-500/20">
                    {top3[0].avatarUrl ? (
                      <img src={top3[0].avatarUrl} alt={top3[0].displayName} className="w-full h-full object-cover" />
                    ) : (
                      <span className="font-bold text-sm text-amber-300">{top3[0].displayName?.charAt(0)}</span>
                    )}
                  </div>
                  <h4 className="text-xs font-black text-amber-300 truncate w-full">
                    {top3[0].displayName}
                  </h4>
                  <span className="text-xs font-black text-amber-400 font-mono mt-0.5">
                    {top3[0].elo} <span className="text-[10px] text-amber-300/70 font-normal">ELO</span>
                  </span>
                </div>
              )}

              {/* 3ème place */}
              {top3[2] ? (
                <div className="bg-gradient-to-t from-dark-900 to-amber-950/30 border border-amber-700/60 rounded-2xl p-3 text-center flex flex-col items-center relative shadow-lg order-3">
                  <div className="w-6 h-6 rounded-full bg-amber-900 text-amber-200 border border-amber-700 flex items-center justify-center text-xs font-black -mt-6 mb-1 shadow">
                    🥉
                  </div>
                  <div className="w-10 h-10 rounded-full border-2 border-amber-700 overflow-hidden mb-1.5 bg-dark-800 flex items-center justify-center">
                    {top3[2].avatarUrl ? (
                      <img src={top3[2].avatarUrl} alt={top3[2].displayName} className="w-full h-full object-cover" />
                    ) : (
                      <span className="font-bold text-xs text-white">{top3[2].displayName?.charAt(0)}</span>
                    )}
                  </div>
                  <h4 className="text-xs font-extrabold text-white truncate w-full">
                    {top3[2].displayName}
                  </h4>
                  <span className="text-[11px] font-black text-amber-300 font-mono mt-0.5">
                    {top3[2].elo} <span className="text-[9px] text-amber-400/60 font-normal">ELO</span>
                  </span>
                </div>
              ) : (
                <div className="order-3" />
              )}
            </div>
          </div>
        )}

        {/* Barre de recherche et Statut Utilisateur */}
        <div className="px-5 py-3 border-b border-slate-800/80 bg-dark-900 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="relative w-full sm:w-64">
            <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Chercher un joueur..."
              className="w-full bg-dark-800/90 border border-slate-700/80 focus:border-amber-500 rounded-xl pl-8 pr-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-amber-500 transition-all"
            />
          </div>

          {currentUser && (
            <div className="text-xs flex items-center space-x-2 text-slate-300 bg-dark-800/80 px-3 py-1.5 rounded-xl border border-slate-700/60 w-full sm:w-auto justify-center sm:justify-start">
              <span className="text-slate-400">Votre position :</span>
              {currentUserRank ? (
                <span className="font-extrabold text-amber-400 font-mono">
                  #{currentUserRank}
                </span>
              ) : (
                <span className="text-slate-400 italic">Hors top 50</span>
              )}
              <span className="text-slate-500">•</span>
              <span className="font-black text-white font-mono">{currentUser.elo} ELO</span>
            </div>
          )}
        </div>

        {/* Liste des joueurs */}
        <div className="flex-1 overflow-y-auto p-3 sm:p-5 space-y-1.5">
          {isLoading && players.length === 0 ? (
            <div className="py-12 flex flex-col items-center justify-center space-y-3">
              <div className="w-8 h-8 border-3 border-amber-500 border-t-transparent rounded-full animate-spin" />
              <p className="text-xs text-slate-400">Chargement des champions...</p>
            </div>
          ) : error ? (
            <div className="py-10 text-center text-xs text-rose-400 bg-rose-500/10 rounded-2xl p-4 border border-rose-500/20">
              {error}
            </div>
          ) : filteredPlayers.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-500">
              {searchQuery ? 'Aucun joueur ne correspond à cette recherche.' : 'Aucun joueur classé pour le moment.'}
            </div>
          ) : (
            filteredPlayers.map((player, idx) => {
              const rank = searchQuery ? players.findIndex((p) => p.id === player.id) + 1 : idx + 1;
              const isMe = currentUser && player.id === currentUser.id;
              const tier = getTierInfo(player.elo);

              return (
                <div
                  key={player.id}
                  className={`flex items-center justify-between p-2.5 sm:px-4 sm:py-3 rounded-2xl border transition-all ${
                    isMe
                      ? 'bg-brand-500/15 border-brand-500/50 shadow-md shadow-brand-500/5'
                      : rank === 1
                      ? 'bg-amber-500/10 border-amber-500/30'
                      : rank === 2
                      ? 'bg-slate-500/10 border-slate-500/30'
                      : rank === 3
                      ? 'bg-amber-800/10 border-amber-800/30'
                      : 'bg-dark-850/60 border-slate-800/80 hover:border-slate-700 hover:bg-dark-800/60'
                  }`}
                >
                  {/* Rang & Joueur */}
                  <div className="flex items-center space-x-3 min-w-0">
                    {/* Numéro de rang */}
                    <div className="w-7 text-center font-mono font-black text-xs shrink-0">
                      {rank === 1 ? (
                        <span className="text-base">🥇</span>
                      ) : rank === 2 ? (
                        <span className="text-base">🥈</span>
                      ) : rank === 3 ? (
                        <span className="text-base">🥉</span>
                      ) : (
                        <span className="text-slate-400">#{rank}</span>
                      )}
                    </div>

                    {/* Avatar */}
                    <div className="w-8 h-8 rounded-full bg-dark-800 border border-slate-700 overflow-hidden flex items-center justify-center shrink-0">
                      {player.avatarUrl ? (
                        <img src={player.avatarUrl} alt={player.displayName} className="w-full h-full object-cover" />
                      ) : (
                        <span className="text-xs font-bold text-white">
                          {player.displayName?.charAt(0).toUpperCase()}
                        </span>
                      )}
                    </div>

                    {/* Nom et tag (Vous) */}
                    <div className="min-w-0">
                      <div className="flex items-center space-x-1.5">
                        <p className={`text-xs font-black truncate ${isMe ? 'text-brand-300' : 'text-white'}`}>
                          {player.displayName}
                        </p>
                        {isMe && (
                          <span className="bg-brand-500 text-white text-[9px] font-black px-1.5 py-0.2 rounded-full uppercase tracking-wider">
                            Vous
                          </span>
                        )}
                      </div>
                      <div className="flex items-center space-x-1 mt-0.5 sm:hidden">
                        <span className={`text-[9px] font-bold px-1.5 py-0.2 rounded border flex items-center space-x-1 ${tier.color}`}>
                          {tier.name}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Tier & Score ELO */}
                  <div className="flex items-center space-x-3 shrink-0">
                    <div className="hidden sm:flex items-center space-x-1">
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border flex items-center space-x-1 ${tier.color}`}>
                        {tier.icon}
                        <span>{tier.name}</span>
                      </span>
                    </div>

                    <div className="text-right font-mono">
                      <span className="text-xs sm:text-sm font-black text-white">
                        {player.elo}
                      </span>
                      <span className="text-[10px] text-slate-400 font-normal ml-1">
                        ELO
                      </span>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer info */}
        <div className="p-3 sm:px-6 border-t border-slate-800/80 bg-dark-950/60 text-[11px] text-slate-400 flex items-center justify-between">
          <div className="flex items-center space-x-1.5">
            <Trophy className="w-3.5 h-3.5 text-amber-400" />
            <span>Les matchs en Versus Classé font évoluer votre score ELO officiel.</span>
          </div>
          <button
            onClick={onClose}
            className="text-xs font-bold text-slate-400 hover:text-white transition-colors cursor-pointer"
          >
            Fermer
          </button>
        </div>
      </div>
    </div>
  );
};

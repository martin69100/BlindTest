import React, { useEffect, useState } from 'react';
import { Trophy, ArrowLeft } from 'lucide-react';
import type { UserThemeStats } from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { userService } from '../services/api';

interface Props {
  onBack: () => void;
}

export const ProfileScreen: React.FC<Props> = ({ onBack }) => {
  const { user } = useAuthStore();
  const [stats, setStats] = useState<UserThemeStats[]>([]);
  const [activeTab, setActiveTab] = useState<'STATS' | 'HISTORY'>('STATS');

  useEffect(() => {
    if (user) {
      userService.getStats(user.id).then(setStats).catch(console.error);
    }
  }, [user]);

  if (!user) return null;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      {/* Bouton Retour */}
      <button
        onClick={onBack}
        className="flex items-center space-x-2 text-xs font-bold text-slate-400 hover:text-white mb-6 transition-colors cursor-pointer"
      >
        <ArrowLeft className="w-4 h-4" />
        <span>Retour au salon</span>
      </button>

      {/* Header Profil */}
      <div className="bg-dark-900 border border-slate-800 rounded-3xl p-6 sm:p-8 mb-8 flex flex-col sm:flex-row items-center sm:items-start justify-between shadow-xl">
        <div className="flex items-center space-x-5 mb-4 sm:mb-0">
          <div className="w-20 h-20 rounded-2xl bg-brand-600/30 border-2 border-brand-500/50 flex items-center justify-center overflow-hidden">
            {user.avatarUrl ? (
              <img src={user.avatarUrl} alt={user.displayName} className="w-full h-full object-cover" />
            ) : (
              <span className="text-2xl font-black text-white">{user.displayName.charAt(0)}</span>
            )}
          </div>
          <div>
            <h2 className="text-2xl font-black text-white tracking-tight">{user.displayName}</h2>
            <p className="text-xs text-slate-400">{user.email}</p>
            <div className="flex items-center space-x-2 mt-2">
              <span className="text-[11px] font-bold text-slate-400">Inscrit depuis le :</span>
              <span className="text-[11px] font-mono text-slate-300">
                {new Date(user.createdAt).toLocaleDateString()}
              </span>
            </div>
          </div>
        </div>

        {/* ELO Card */}
        <div className="bg-dark-950 border border-brand-500/30 rounded-2xl p-5 text-center min-w-[160px]">
          <div className="flex items-center justify-center space-x-1.5 text-xs font-bold text-amber-400 mb-1">
            <Trophy className="w-4 h-4" />
            <span>CLASSEMENT ELO</span>
          </div>
          <p className="text-3xl font-black text-white">{user.elo}</p>
          <span className="text-[10px] text-slate-400">Score compétitif global</span>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex space-x-2 mb-6 border-b border-slate-800 pb-2">
        <button
          onClick={() => setActiveTab('STATS')}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-colors cursor-pointer ${
            activeTab === 'STATS'
              ? 'bg-brand-600 text-white shadow-md'
              : 'text-slate-400 hover:text-white'
          }`}
        >
          Maîtrise par Thème
        </button>
        <button
          onClick={() => setActiveTab('HISTORY')}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-colors cursor-pointer ${
            activeTab === 'HISTORY'
              ? 'bg-brand-600 text-white shadow-md'
              : 'text-slate-400 hover:text-white'
          }`}
        >
          Historique des Matchs
        </button>
      </div>

      {/* Tab Content : Statistiques par thème */}
      {activeTab === 'STATS' && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {stats.length > 0 ? (
            stats.map((item) => {
              const totalCorrect = item.soloCorrectAnswers + item.versusCorrectAnswers;
              const masteryLevel = Math.min(100, item.masteryPoints || (totalCorrect * 5));

              return (
                <div key={item.id} className="bg-dark-900 border border-slate-800 rounded-2xl p-5 shadow-md">
                  <div className="flex items-center justify-between mb-3">
                    <h4 className="text-sm font-bold text-white">{item.theme.name}</h4>
                    <span className="text-xs font-mono font-bold text-brand-400">{masteryLevel}%</span>
                  </div>

                  {/* Barre de maîtrise */}
                  <div className="w-full h-2 bg-dark-950 rounded-full overflow-hidden mb-4 border border-slate-800">
                    <div
                      className="h-full bg-gradient-to-r from-brand-600 to-indigo-500 rounded-full transition-all duration-500"
                      style={{ width: `${masteryLevel}%` }}
                    />
                  </div>

                  <div className="grid grid-cols-3 gap-2 text-center text-[11px] text-slate-400">
                    <div className="bg-dark-950 p-2 rounded-xl">
                      <span className="block font-bold text-white">{item.soloGamesPlayed}</span>
                      <span>Parties solo</span>
                    </div>
                    <div className="bg-dark-950 p-2 rounded-xl">
                      <span className="block font-bold text-emerald-400">{totalCorrect}</span>
                      <span>Titres trouvés</span>
                    </div>
                    <div className="bg-dark-950 p-2 rounded-xl">
                      <span className="block font-bold text-amber-400">{item.bonusCorrectAnswers}</span>
                      <span>Bonus réussis</span>
                    </div>
                  </div>
                </div>
              );
            })
          ) : (
            <div className="col-span-2 py-12 text-center text-slate-500 text-sm">
              Aucune statistique enregistrée pour le moment. Lancez une partie solo ou versus !
            </div>
          )}
        </div>
      )}

      {/* Tab Content : Historique */}
      {activeTab === 'HISTORY' && (
        <div className="space-y-3">
          <div className="py-12 text-center text-slate-500 text-sm bg-dark-900 border border-slate-800 rounded-2xl">
            Historique des parties classées en cours d'enregistrement...
          </div>
        </div>
      )}
    </div>
  );
};

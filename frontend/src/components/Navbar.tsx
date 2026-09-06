import React from 'react';
import { Music, Trophy, User as UserIcon, LogOut } from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { useGameStore } from '../store/useGameStore';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuthStore();
  const { phase, resetGame } = useGameStore();

  const getEloBadgeColor = (elo: number) => {
    if (elo >= 1500) return 'bg-amber-500/20 text-amber-300 border-amber-500/50';
    if (elo >= 1200) return 'bg-cyan-500/20 text-cyan-300 border-cyan-500/50';
    return 'bg-violet-500/20 text-violet-300 border-violet-500/50';
  };

  return (
    <header className="border-b border-dark-800 bg-dark-900/80 backdrop-blur-md sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand */}
        <div 
          onClick={() => { if (phase === 'LOBBY' || phase === 'FINISHED') resetGame(); }}
          className="flex items-center space-x-3 cursor-pointer group"
        >
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-brand-500/20 group-hover:scale-105 transition-transform">
            <Music className="w-5 h-5 text-white animate-pulse" />
          </div>
          <div>
            <h1 className="text-lg font-black tracking-wider bg-gradient-to-r from-white via-slate-200 to-brand-200 bg-clip-text text-transparent">
              BEAT<span className="text-brand-500">RIVAL</span>
            </h1>
            <p className="text-[10px] text-slate-400 uppercase tracking-widest font-semibold">Blind Test Compétitif</p>
          </div>
        </div>

        {/* User Stats & Controls */}
        {user ? (
          <div className="flex items-center space-x-4">
            {/* ELO Badge */}
            <div className={`flex items-center space-x-2 px-3 py-1.5 rounded-full border text-xs font-bold shadow-inner ${getEloBadgeColor(user.elo)}`}>
              <Trophy className="w-3.5 h-3.5" />
              <span>{user.elo} ELO</span>
            </div>

            {/* Profile pill */}
            <div className="flex items-center space-x-2.5 bg-dark-800/80 border border-slate-800 px-3 py-1.5 rounded-full">
              {user.avatarUrl ? (
                <img src={user.avatarUrl} alt={user.displayName} className="w-6 h-6 rounded-full border border-brand-500/50" />
              ) : (
                <div className="w-6 h-6 rounded-full bg-brand-600 flex items-center justify-center text-xs font-bold">
                  {user.displayName.charAt(0).toUpperCase()}
                </div>
              )}
              <span className="text-xs font-semibold text-slate-200 max-w-[120px] truncate">{user.displayName}</span>
            </div>

            {/* Logout / Switch */}
            <button
              onClick={logout}
              title="Changer d'utilisateur ou déconnexion"
              className="p-2 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <div className="flex items-center space-x-2 text-xs text-slate-400">
            <UserIcon className="w-4 h-4" />
            <span>Non connecté</span>
          </div>
        )}
      </div>
    </header>
  );
};

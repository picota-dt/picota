import {Plus, Layers} from "lucide-react";

interface EmptyStateProps {
    onCreateNew: () => void;
    isFiltered?: boolean;
    onClearFilters?: () => void;
}

export function EmptyState({onCreateNew, isFiltered, onClearFilters}: EmptyStateProps) {
    if (isFiltered) {
        return (
            <div className="flex flex-col items-center justify-center py-24 text-center">
                <div
                    className="w-14 h-14 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mb-4">
                    <Layers className="w-6 h-6 text-white/20"/>
                </div>
                <p className="text-white/50 mb-1" style={{fontWeight: 500}}>No twins match your filters</p>
                <p className="text-white/25 text-sm">Try adjusting your search or filter criteria.</p>
                <button
                    onClick={onClearFilters}
                    className="mt-4 text-cyan-500 hover:text-cyan-300 text-sm transition-colors"
                >
                    Clear filters
                </button>
            </div>
        );
    }

    return (
        <div className="flex flex-col items-center justify-center py-20 text-center px-4">
            {/* Decorative icon area */}
            <div className="relative mb-6">
                <div
                    className="w-24 h-24 rounded-3xl bg-gradient-to-br from-cyan-500/10 to-blue-600/10 border border-cyan-500/20 flex items-center justify-center">
                    <Layers className="w-10 h-10 text-cyan-500/60"/>
                </div>
                <div
                    className="absolute -top-1 -right-1 w-6 h-6 rounded-full bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center">
                    <Plus className="w-3.5 h-3.5 text-cyan-400"/>
                </div>
            </div>

            <h3 className="text-white mb-2" style={{fontWeight: 600, fontSize: "1.1rem"}}>
                No digital twins yet
            </h3>
            <p className="text-white/40 text-sm max-w-sm mb-6 leading-relaxed">
                You haven't created any digital twins yet. Start by creating your first one — model any physical asset,
                system, or process.
            </p>

            <button
                onClick={onCreateNew}
                className="flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 text-white px-5 py-2.5 rounded-xl text-sm transition-colors shadow-lg shadow-cyan-500/20"
                style={{fontWeight: 500}}
            >
                <Plus className="w-4 h-4"/>
                Create your first twin
            </button>
        </div>
    );
}

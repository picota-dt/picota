import {useState, useMemo} from "react";
import {Plus, Search, X, LayoutGrid, List, SlidersHorizontal} from "lucide-react";
import {useNavigate} from "react-router";
import {useApp} from "../context/AppContext";
import {TwinCard} from "./TwinCard";
import {EmptyState} from "./EmptyState";
import {CreateTwinModal} from "./CreateTwinModal";
import {ListRow} from "./ListRow";

const STATUS_FILTERS = [
    {label: "All", value: "all"},
    {label: "Active", value: "active"},
    {label: "Draft", value: "draft"},
    {label: "Offline", value: "offline"},
];

export function CatalogPage() {
    const navigate = useNavigate();
    const {twins, createTwin} = useApp();

    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState("all");
    const [typeFilter, setTypeFilter] = useState("All types");
    const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
    const [showModal, setShowModal] = useState(false);
    const [showFilterBar, setShowFilterBar] = useState(false);

    const allTypes = useMemo(
        () => ["All types", ...Array.from(new Set(twins.map((t) => t.type)))],
        [twins]
    );

    const filtered = useMemo(() => {
        let items = twins;
        if (search.trim()) {
            const q = search.toLowerCase();
            items = items.filter(
                (t) =>
                    t.name.toLowerCase().includes(q) ||
                    t.description.toLowerCase().includes(q) ||
                    t.type.toLowerCase().includes(q)
            );
        }
        if (statusFilter !== "all") items = items.filter((t) => t.status === statusFilter);
        if (typeFilter !== "All types") items = items.filter((t) => t.type === typeFilter);
        return items;
    }, [twins, search, statusFilter, typeFilter]);

    const hasFilters = search || statusFilter !== "all" || typeFilter !== "All types";

    const clearFilters = () => {
        setSearch("");
        setStatusFilter("all");
        setTypeFilter("All types");
    };

    const handleCreate = async (name: string, description: string, type: string) => {
        const images = [
            "https://images.unsplash.com/photo-1649829725145-d93731589a6e?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=1080",
            "https://images.unsplash.com/photo-1563968743333-044cef800494?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=1080",
            "https://images.unsplash.com/photo-1647427060118-4911c9821b82?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=1080",
        ];
        const img = images[Math.floor(Math.random() * images.length)];
        try {
            const newTwin = await createTwin(name, description, type, img);
            navigate(`/twins/${newTwin.id}`);
        } catch (err) {
            console.error(err);
        }
    };

    const stats = {
        total: twins.length,
        active: twins.filter((t) => t.status === "active").length,
        draft: twins.filter((t) => t.status === "draft").length,
    };

    return (
        <div className="min-h-screen bg-[#0f1117]">
            {/* Top bar */}
            <div className="border-b border-white/6">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div>
                            <h1 className="text-white"
                                style={{fontWeight: 700, fontSize: "clamp(1.3rem, 3vw, 1.7rem)"}}>
                                My Digital Twins
                            </h1>
                            <p className="text-white/40 text-sm mt-0.5">
                                Manage and monitor your digital replicas
                            </p>
                        </div>
                        <button
                            onClick={() => setShowModal(true)}
                            className="flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 text-white px-4 py-2.5 rounded-xl text-sm transition-all shadow-lg shadow-cyan-500/20 self-start sm:self-auto"
                            style={{fontWeight: 500}}
                        >
                            <Plus className="w-4 h-4"/>
                            New Twin
                        </button>
                    </div>

                    {/* Stats */}
                    {twins.length > 0 && (
                        <div className="flex items-center gap-5 mt-5 pt-5 border-t border-white/6">
                            <div className="flex items-center gap-2 text-sm">
                                <span className="text-white/30">Total</span>
                                <span className="text-white" style={{fontWeight: 600}}>{stats.total}</span>
                            </div>
                            <div className="w-px h-4 bg-white/10"/>
                            <div className="flex items-center gap-1.5 text-sm">
                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"/>
                                <span className="text-white/30">Active</span>
                                <span className="text-emerald-400" style={{fontWeight: 600}}>{stats.active}</span>
                            </div>
                            <div className="w-px h-4 bg-white/10"/>
                            <div className="flex items-center gap-2 text-sm">
                                <span className="text-white/30">Drafts</span>
                                <span className="text-amber-400" style={{fontWeight: 600}}>{stats.draft}</span>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* Content */}
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                {twins.length > 0 && (
                    <>
                        {/* Search + filter row */}
                        <div className="flex items-center gap-2 mb-4 flex-wrap">
                            <div className="relative flex-1 min-w-[180px] max-w-sm">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/25"/>
                                <input
                                    type="text"
                                    placeholder="Search twins..."
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl pl-9 pr-8 py-2 text-white placeholder-white/25 text-sm outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                                />
                                {search && (
                                    <button
                                        onClick={() => setSearch("")}
                                        className="absolute right-2.5 top-1/2 -translate-y-1/2 text-white/25 hover:text-white/50"
                                    >
                                        <X className="w-3.5 h-3.5"/>
                                    </button>
                                )}
                            </div>

                            <button
                                onClick={() => setShowFilterBar(!showFilterBar)}
                                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl border text-sm transition-all ${
                                    showFilterBar || statusFilter !== "all" || typeFilter !== "All types"
                                        ? "bg-cyan-500/15 border-cyan-500/40 text-cyan-400"
                                        : "bg-white/5 border-white/10 text-white/40 hover:text-white/60 hover:border-white/20"
                                }`}
                            >
                                <SlidersHorizontal className="w-4 h-4"/>
                                <span className="hidden sm:inline">Filters</span>
                                {(statusFilter !== "all" || typeFilter !== "All types") && (
                                    <span className="w-1.5 h-1.5 rounded-full bg-cyan-400"/>
                                )}
                            </button>

                            {hasFilters && (
                                <button
                                    onClick={clearFilters}
                                    className="flex items-center gap-1 text-white/30 hover:text-white/50 text-sm transition-colors"
                                >
                                    <X className="w-3.5 h-3.5"/>
                                    <span className="hidden sm:inline">Clear</span>
                                </button>
                            )}

                            <div className="flex bg-white/5 border border-white/10 rounded-xl overflow-hidden ml-auto">
                                <button
                                    onClick={() => setViewMode("grid")}
                                    className={`p-2 transition-colors ${viewMode === "grid" ? "bg-cyan-500 text-white" : "text-white/30 hover:text-white/60"}`}
                                >
                                    <LayoutGrid className="w-4 h-4"/>
                                </button>
                                <button
                                    onClick={() => setViewMode("list")}
                                    className={`p-2 transition-colors ${viewMode === "list" ? "bg-cyan-500 text-white" : "text-white/30 hover:text-white/60"}`}
                                >
                                    <List className="w-4 h-4"/>
                                </button>
                            </div>
                        </div>

                        {/* Filter bar */}
                        {showFilterBar && (
                            <div className="bg-white/4 border border-white/8 rounded-2xl p-4 mb-4 flex flex-wrap gap-5">
                                <div>
                                    <p className="text-white/30 text-xs uppercase tracking-wider mb-2">Status</p>
                                    <div className="flex gap-2 flex-wrap">
                                        {STATUS_FILTERS.map((f) => (
                                            <button
                                                key={f.value}
                                                onClick={() => setStatusFilter(f.value)}
                                                className={`px-3 py-1.5 rounded-lg text-xs border transition-all ${
                                                    statusFilter === f.value
                                                        ? "bg-cyan-500/20 border-cyan-500/50 text-cyan-300"
                                                        : "bg-white/4 border-white/10 text-white/40 hover:border-white/20 hover:text-white/60"
                                                }`}
                                            >
                                                {f.label}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                                <div>
                                    <p className="text-white/30 text-xs uppercase tracking-wider mb-2">Type</p>
                                    <div className="flex flex-wrap gap-2">
                                        {allTypes.map((t) => (
                                            <button
                                                key={t}
                                                onClick={() => setTypeFilter(t)}
                                                className={`px-3 py-1.5 rounded-lg text-xs border transition-all ${
                                                    typeFilter === t
                                                        ? "bg-cyan-500/20 border-cyan-500/50 text-cyan-300"
                                                        : "bg-white/4 border-white/10 text-white/40 hover:border-white/20 hover:text-white/60"
                                                }`}
                                            >
                                                {t}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        )}

                        <p className="text-white/25 text-xs mb-4">
                            {filtered.length} {filtered.length === 1 ? "twin" : "twins"} found
                            {hasFilters && " with current filters"}
                        </p>
                    </>
                )}

                {/* Grid / List / Empty */}
                {twins.length === 0 ? (
                    <EmptyState onCreateNew={() => setShowModal(true)}/>
                ) : filtered.length === 0 ? (
                    <EmptyState isFiltered onClearFilters={clearFilters} onCreateNew={() => setShowModal(true)}/>
                ) : viewMode === "grid" ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        {filtered.map((twin) => (
                            <TwinCard key={twin.id} twin={twin}/>
                        ))}
                        <button
                            onClick={() => setShowModal(true)}
                            className="border-2 border-dashed border-white/10 hover:border-cyan-500/40 rounded-2xl min-h-[220px] flex flex-col items-center justify-center gap-2 text-white/25 hover:text-cyan-400/60 transition-all duration-300 group"
                        >
                            <div
                                className="w-10 h-10 rounded-xl border-2 border-dashed border-current flex items-center justify-center group-hover:scale-110 transition-transform">
                                <Plus className="w-5 h-5"/>
                            </div>
                            <span className="text-sm">Create new twin</span>
                        </button>
                    </div>
                ) : (
                    <div className="flex flex-col gap-3">
                        {filtered.map((twin) => (
                            <ListRow key={twin.id} twin={twin}/>
                        ))}
                        <button
                            onClick={() => setShowModal(true)}
                            className="flex items-center gap-3 border border-dashed border-white/10 hover:border-cyan-500/40 rounded-xl p-4 text-white/25 hover:text-cyan-400/60 transition-all group"
                        >
                            <div
                                className="w-8 h-8 rounded-lg border border-dashed border-current flex items-center justify-center">
                                <Plus className="w-4 h-4"/>
                            </div>
                            <span className="text-sm">Create new twin</span>
                        </button>
                    </div>
                )}
            </div>

            {showModal && (
                <CreateTwinModal onClose={() => setShowModal(false)} onCreate={handleCreate}/>
            )}
        </div>
    );
}

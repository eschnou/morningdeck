import { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Plus } from 'lucide-react';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  arrayMove,
} from '@dnd-kit/sortable';
import { apiClient } from '@/lib/api';
import { events, BRIEFS_CHANGED } from '@/lib/events';
import { toast } from '@/hooks/use-toast';
import { CreateBriefDialog } from '@/components/briefs/CreateBriefDialog';
import { SortableBriefItem } from '@/components/layout/SortableBriefItem';
import { Button } from '@/components/ui/button';
import {
  SidebarGroup,
  SidebarGroupAction,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuSkeleton,
} from '@/components/ui/sidebar';
import type { DayBriefDTO } from '@/types';

export function SidebarBriefsList() {
  const location = useLocation();
  const navigate = useNavigate();
  const [briefs, setBriefs] = useState<DayBriefDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showCreateDialog, setShowCreateDialog] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    }),
    useSensor(TouchSensor, {
      activationConstraint: { delay: 250, tolerance: 5 },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const fetchBriefs = async () => {
    try {
      const response = await apiClient.getDayBriefs(0, 50);
      setBriefs(response.content);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load briefs',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchBriefs();
    const unsubscribe = events.on(BRIEFS_CHANGED, fetchBriefs);
    return unsubscribe;
  }, []);

  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const oldIndex = briefs.findIndex((b) => b.id === active.id);
    const newIndex = briefs.findIndex((b) => b.id === over.id);

    // Optimistic update
    const previousBriefs = [...briefs];
    const newOrder = arrayMove(briefs, oldIndex, newIndex);
    setBriefs(newOrder);

    try {
      await apiClient.reorderBriefs(newOrder.map((b) => b.id));
    } catch (error) {
      // Revert on failure
      setBriefs(previousBriefs);
      toast({
        title: 'Failed to save order',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
      // Refresh in case of concurrent modifications
      events.emit(BRIEFS_CHANGED);
    }
  };

  const handleCreateSuccess = (brief: DayBriefDTO) => {
    setBriefs((prev) => [...prev, brief]);
    navigate(`/briefs/${brief.id}`);
  };

  const isActive = (briefId: string) => {
    return (
      location.pathname === `/briefs/${briefId}` ||
      location.pathname.startsWith(`/briefs/${briefId}/`)
    );
  };

  return (
    <>
      <SidebarGroup>
        <SidebarGroupLabel>Briefs</SidebarGroupLabel>
        <SidebarGroupAction onClick={() => setShowCreateDialog(true)} title="Create Brief">
          <Plus className="size-4" />
        </SidebarGroupAction>
        <SidebarGroupContent>
          <SidebarMenu>
            {isLoading ? (
              <>
                <SidebarMenuSkeleton showIcon />
                <SidebarMenuSkeleton showIcon />
                <SidebarMenuSkeleton showIcon />
              </>
            ) : briefs.length === 0 ? (
              <SidebarMenuItem>
                <div className="px-2 py-4 text-center">
                  <p className="text-xs text-muted-foreground mb-2">No briefs yet</p>
                  <Button variant="outline" size="sm" onClick={() => setShowCreateDialog(true)}>
                    <Plus className="mr-1 h-3 w-3" />
                    Create Brief
                  </Button>
                </div>
              </SidebarMenuItem>
            ) : (
              <DndContext
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragEnd={handleDragEnd}
              >
                <SortableContext items={briefs.map((b) => b.id)} strategy={verticalListSortingStrategy}>
                  {briefs.map((brief) => (
                    <SortableBriefItem
                      key={brief.id}
                      brief={brief}
                      isActive={isActive(brief.id)}
                      onClick={() => navigate(`/briefs/${brief.id}`)}
                    />
                  ))}
                </SortableContext>
              </DndContext>
            )}
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>

      <CreateBriefDialog
        open={showCreateDialog}
        onOpenChange={setShowCreateDialog}
        onSuccess={handleCreateSuccess}
      />
    </>
  );
}

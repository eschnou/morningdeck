import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { FileText } from 'lucide-react';
import {
  SidebarMenuItem,
  SidebarMenuButton,
} from '@/components/ui/sidebar';
import type { DayBriefDTO } from '@/types';

interface SortableBriefItemProps {
  brief: DayBriefDTO;
  isActive: boolean;
  onClick: () => void;
}

export function SortableBriefItem({ brief, isActive, onClick }: SortableBriefItemProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: brief.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <SidebarMenuItem ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <SidebarMenuButton
        isActive={isActive}
        onClick={onClick}
        tooltip={brief.title}
        className="cursor-grab active:cursor-grabbing"
      >
        <FileText className="size-4" />
        <span>{brief.title}</span>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

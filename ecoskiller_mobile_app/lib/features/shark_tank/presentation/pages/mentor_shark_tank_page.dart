import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class MentorSharkTankPage extends StatefulWidget {
  final Map<String, dynamic> user;
  const MentorSharkTankPage({super.key, required this.user});

  @override
  State<MentorSharkTankPage> createState() => _MentorSharkTankPageState();
}

class _MentorSharkTankPageState extends State<MentorSharkTankPage> {
  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      body: CustomScrollView(
        slivers: [
          _buildAppBar(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildSectionHeader('ASSIGNED IDEAS'),
                  const SizedBox(height: 16),
                  _buildAssignedList(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('MENTORSHIP STATS'),
                  const SizedBox(height: 16),
                  _buildMentorStats(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('UPCOMING SESSIONS'),
                  const SizedBox(height: 16),
                  _buildSessionsList(),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppBar(BuildContext context) {
    return SliverAppBar(
      expandedHeight: 120,
      pinned: true,
      title: Text(
        'MENTOR NODE',
        style: GoogleFonts.inter(fontWeight: FontWeight.w900, letterSpacing: 2, fontSize: 16),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 11,
        fontWeight: FontWeight.w900,
        letterSpacing: 2,
      ),
    );
  }

  Widget _buildAssignedList() {
    return Column(
      children: [
        _AssignedItem('AI Waste Management', 'Pending Review', Colors.orange),
        const SizedBox(height: 12),
        _AssignedItem('Solar Irrigation', 'Reviewed', Colors.green),
      ],
    );
  }

  Widget _buildMentorStats() {
    return Row(
      children: [
        _StatCard('Hours', '140', Colors.blue),
        const SizedBox(width: 16),
        _StatCard('Mentees', '12', Colors.purple),
      ],
    );
  }

  Widget _buildSessionsList() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          const Icon(LucideIcons.calendar, color: AppTheme.linkedinBlue),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Pitch Prep: Round 2', style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
              Text('Tomorrow at 4:00 PM', style: GoogleFonts.inter(fontSize: 12, color: Colors.grey)),
            ],
          ),
          const Spacer(),
          const Icon(LucideIcons.chevronRight, color: Colors.grey),
        ],
      ),
    );
  }
}

class _AssignedItem extends StatelessWidget {
  final String title;
  final String status;
  final Color color;
  const _AssignedItem(this.title, this.status, this.color);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
              Text(status, style: GoogleFonts.inter(fontSize: 12, color: color)),
            ],
          ),
          const Icon(LucideIcons.chevronRight, color: Colors.grey),
        ],
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final Color color;
  const _StatCard(this.label, this.value, this.color);

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Column(
          children: [
            Text(value, style: GoogleFonts.inter(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
            Text(label, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey, fontWeight: FontWeight.bold)),
          ],
        ),
      ),
    );
  }
}

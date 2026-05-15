import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';
import 'package:ecoskiller_mobile_app/features/auth/presentation/pages/login_page.dart';
import 'package:ecoskiller_mobile_app/features/auth/data/providers/auth_service.dart';
import 'package:ecoskiller_mobile_app/features/shark_tank/presentation/pages/shark_tank_home_page.dart';

class StudentDashboardPage extends StatelessWidget {
  final Map<String, dynamic> user;

  const StudentDashboardPage({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      body: CustomScrollView(
        slivers: [
          _buildSliverAppBar(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  FadeInDown(
                    child: _buildWelcomeCard(),
                  ),
                  const SizedBox(height: 24),
                  _buildSectionTitle('SOVEREIGN NODE STATUS'),
                  const SizedBox(height: 16),
                  _buildStatusGrid(),
                  const SizedBox(height: 32),
                  _buildSectionTitle('ACTIVE LEARNING PATHS'),
                  const SizedBox(height: 16),
                  _buildLearningPaths(),
                  const SizedBox(height: 32),
                  _buildSectionTitle('PREMIUM ECOSYSTEMS'),
                  const SizedBox(height: 16),
                  _buildSharkTankCard(context),
                ],
              ),
            ),
          ),
        ],
      ),
      bottomNavigationBar: _buildBottomNav(),
    );
  }

  Widget _buildSliverAppBar(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;
    return SliverAppBar(
      expandedHeight: 120,
      floating: false,
      pinned: true,
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      elevation: 0,
      flexibleSpace: FlexibleSpaceBar(
        titlePadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        title: Text(
          'ECOSKILLER',
          style: GoogleFonts.inter(
            color: isDark ? AppTheme.crispText : AppTheme.deepOcean,
            fontWeight: FontWeight.w900,
            fontSize: 18,
            letterSpacing: 2,
          ),
        ),
      ),
      actions: [
        IconButton(
          icon: const Icon(LucideIcons.bell, color: AppTheme.linkedinBlue),
          onPressed: () {},
        ),
        IconButton(
          icon: const Icon(LucideIcons.logOut, color: Colors.redAccent),
          onPressed: () async {
            await AuthService().logout();
            if (context.mounted) {
              Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (context) => const LoginPage()),
                (route) => false,
              );
            }
          },
        ),
        const SizedBox(width: 8),
        CircleAvatar(
          radius: 18,
          backgroundColor: AppTheme.linkedinBlue.withOpacity(0.1),
          child: Text(
            user['name']?[0]?.toUpperCase() ?? 'U',
            style: const TextStyle(color: AppTheme.linkedinBlue, fontWeight: FontWeight.bold),
          ),
        ),
        const SizedBox(width: 20),
      ],
    );
  }

  Widget _buildWelcomeCard() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppTheme.linkedinBlue, Color(0xFF005582)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: AppTheme.linkedinBlue.withOpacity(0.3),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Welcome, ${user['name']}',
                style: GoogleFonts.inter(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const Icon(LucideIcons.shieldCheck, color: Colors.white70, size: 24),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'EUID: ${user['euid']}',
            style: GoogleFonts.jetBrainsMono(
              color: Colors.white60,
              fontSize: 11,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: 24),
          Row(
            children: [
              _buildStatItem('SKILL SCORE', '842'),
              const Spacer(),
              _buildStatItem('RANK', '#12'),
              const Spacer(),
              _buildStatItem('LEVEL', 'L4'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildStatItem(String label, String value) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: GoogleFonts.inter(
            color: Colors.white54,
            fontSize: 9,
            fontWeight: FontWeight.w900,
            letterSpacing: 1,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: GoogleFonts.inter(
            color: Colors.white,
            fontSize: 18,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 11,
        fontWeight: FontWeight.w900,
        letterSpacing: 1.5,
      ),
    );
  }

  Widget _buildStatusGrid() {
    return GridView.count(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisCount: 2,
      crossAxisSpacing: 16,
      mainAxisSpacing: 16,
      childAspectRatio: 1.5,
      children: [
        _buildStatusCard(LucideIcons.activity, 'NEURAL SYNC', 'OPTIMIZED', Colors.green),
        _buildStatusCard(LucideIcons.database, 'LOCAL DATA', 'SYNCED', AppTheme.linkedinBlue),
      ],
    );
  }

  Widget _buildStatusCard(IconData icon, String title, String status, Color color) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.linkedinBlue.withOpacity(0.1)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Icon(icon, color: color, size: 20),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.grey),
              ),
              Text(
                status,
                style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w900, color: color),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildLearningPaths() {
    return Column(
      children: [
        _buildPathCard('Cyber Security Architecture', '75%', Colors.orange),
        const SizedBox(height: 12),
        _buildPathCard('Advanced Flutter Engineering', '42%', AppTheme.linkedinBlue),
      ],
    );
  }

  Widget _buildPathCard(String title, String progress, Color color) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: color.withOpacity(0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(LucideIcons.bookOpen, color: color, size: 20),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                LinearProgressIndicator(
                  value: double.parse(progress.replaceAll('%', '')) / 100,
                  backgroundColor: color.withOpacity(0.1),
                  valueColor: AlwaysStoppedAnimation<Color>(color),
                ),
              ],
            ),
          ),
          const SizedBox(width: 16),
          Text(
            progress,
            style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.bold, color: color),
          ),
        ],
      ),
    );
  }

  Widget _buildSharkTankCard(BuildContext context) {
    return GestureDetector(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => SharkTankHomePage(user: user),
          ),
        );
      },
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.blue.withOpacity(0.2),
              blurRadius: 15,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(LucideIcons.waves, color: Colors.cyanAccent, size: 28),
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'SHARK TANK',
                    style: GoogleFonts.inter(
                      color: Colors.white,
                      fontWeight: FontWeight.w900,
                      fontSize: 16,
                      letterSpacing: 1.5,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Pitch your ideas to top investors',
                    style: GoogleFonts.inter(color: Colors.white70, fontSize: 12),
                  ),
                ],
              ),
            ),
            const Icon(LucideIcons.chevronRight, color: Colors.white54),
          ],
        ),
      ),
    );
  }

  Widget _buildBottomNav() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 20),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 20,
          ),
        ],
      ),
      child: const Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          Icon(LucideIcons.home, color: AppTheme.linkedinBlue),
          Icon(LucideIcons.search, color: Colors.grey),
          Icon(LucideIcons.layers, color: Colors.grey),
          Icon(LucideIcons.user, color: Colors.grey),
        ],
      ),
    );
  }
}
